// Portal-deployable variant of main.bicep.
//
// Same topology, but images are pulled with ACR admin credentials instead of a
// managed identity, because creating a role assignment is awkward in a portal-only
// flow. Prefer main.bicep when the Azure CLI is available.
//
// Prerequisite: the registry must already exist with the ADMIN USER ENABLED, and
// all seven images must already be pushed to it.

targetScope = 'resourceGroup'

@description('Region for all resources.')
param location string = resourceGroup().location

@description('Prefix for resource names.')
param namePrefix string = 'pulseops'

@description('Name of the existing container registry, admin user enabled.')
param acrName string

@description('Image tag that was pushed, for example v1.')
param imageTag string

@description('PostgreSQL administrator login. Must not appear in the password.')
param postgresAdminUser string = 'pulseops'

@secure()
@description('PostgreSQL password: 8-128 chars, 3 of upper/lower/digit/symbol.')
param postgresAdminPassword string

@secure()
@description('LLM API key. Leave empty to run the deterministic offline provider.')
param llmApiKey string = ''

@description('For Azure OpenAI use https://<resource>.openai.azure.com/openai/v1')
param llmBaseUrl string = 'https://api.openai.com/v1'

@description('For Azure OpenAI this is the deployment name, not the model name.')
param llmModel string = 'gpt-4.1-mini'

@allowed(['bearer', 'api-key'])
param llmAuthHeader string = 'bearer'

@description('Public IP allowed to reach the ShopFlow demo endpoints. Empty means open to the internet, which also exposes the failure-injection endpoint.')
param allowedClientIp string = ''

@description('PostgreSQL major version. Lower this if the region reports the version as unavailable.')
param postgresVersion string = '16'

@description('Compute size. Burstable is cheapest but is not offered in every region.')
param postgresSkuName string = 'Standard_B1ms'

@allowed(['Burstable', 'GeneralPurpose', 'MemoryOptimized'])
param postgresSkuTier string = 'Burstable'

var dbName = 'pulseops'
var pgName = toLower('${namePrefix}-pg-${uniqueString(resourceGroup().id)}')

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: acrName
}

resource logs 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: '${namePrefix}-logs'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

resource env 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${namePrefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

// ---------------------------------------------------------------------------
// PostgreSQL
// ---------------------------------------------------------------------------

resource pg 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: pgName
  location: location
  sku: {
    name: postgresSkuName
    tier: postgresSkuTier
  }
  properties: {
    version: postgresVersion
    administratorLogin: postgresAdminUser
    administratorLoginPassword: postgresAdminPassword
    storage: { storageSizeGB: 32 }
    backup: { backupRetentionDays: 7 }
    highAvailability: { mode: 'Disabled' }
  }
}

resource pgDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: pg
  name: dbName
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// The environment has no VNet, so container apps reach Postgres over the public
// endpoint. This rule is the documented "allow Azure services" special case.
resource pgFirewall 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: pg
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// ---------------------------------------------------------------------------
// Shared container configuration
// ---------------------------------------------------------------------------

var registries = [
  {
    server: acr.properties.loginServer
    username: acr.listCredentials().username
    passwordSecretRef: 'acr-password'
  }
]

// Every app pulls from the registry, so every app carries this secret.
var acrSecret = {
  name: 'acr-password'
  value: acr.listCredentials().passwords[0].value
}

var dbSecret = {
  name: 'postgres-password'
  value: postgresAdminPassword
}

var dbEnv = [
  {
    name: 'SPRING_DATASOURCE_URL'
    // Flexible Server requires TLS; set here so no application.yml has to change.
    value: 'jdbc:postgresql://${pg.properties.fullyQualifiedDomainName}:5432/${dbName}?sslmode=require'
  }
  {
    name: 'POSTGRES_USER'
    value: postgresAdminUser
  }
  {
    name: 'POSTGRES_PASSWORD'
    secretRef: 'postgres-password'
  }
]

// Apps in an environment resolve each other by app name.
var kafkaEnv = [
  {
    name: 'KAFKA_BOOTSTRAP_SERVERS'
    value: 'kafka:9092'
  }
]

var llmSecrets = empty(llmApiKey) ? [] : [
  {
    name: 'llm-api-key'
    value: llmApiKey
  }
]

var llmEnv = empty(llmApiKey) ? [] : [
  {
    name: 'LLM_API_KEY'
    secretRef: 'llm-api-key'
  }
  {
    name: 'LLM_BASE_URL'
    value: llmBaseUrl
  }
  {
    name: 'LLM_MODEL'
    value: llmModel
  }
  {
    name: 'LLM_AUTH_HEADER'
    value: llmAuthHeader
  }
]

// Applied to the ShopFlow apps, which expose the failure-injection endpoint.
var demoIpRestrictions = empty(allowedClientIp) ? [] : [
  {
    name: 'allow-operator'
    action: 'Allow'
    ipAddressRange: '${allowedClientIp}/32'
  }
]

// ---------------------------------------------------------------------------
// Kafka
// ---------------------------------------------------------------------------

resource kafka 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'kafka'
  location: location
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: registries
      secrets: [acrSecret]
      ingress: {
        external: false
        transport: 'tcp'
        targetPort: 9092
        exposedPort: 9092
      }
    }
    template: {
      containers: [
        {
          name: 'kafka'
          image: '${acr.properties.loginServer}/pulseops-kafka:${imageTag}'
          resources: {
            cpu: json('1.0')
            memory: '2Gi'
          }
          env: [
            { name: 'KAFKA_NODE_ID', value: '1' }
            { name: 'KAFKA_PROCESS_ROLES', value: 'broker,controller' }
            { name: 'CLUSTER_ID', value: 'pulseops-azure-cluster' }
            { name: 'KAFKA_LISTENERS', value: 'INTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093' }
            // Clients reach this app as kafka:9092, so that is what the broker must advertise.
            { name: 'KAFKA_ADVERTISED_LISTENERS', value: 'INTERNAL://kafka:9092' }
            { name: 'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP', value: 'CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT' }
            { name: 'KAFKA_INTER_BROKER_LISTENER_NAME', value: 'INTERNAL' }
            { name: 'KAFKA_CONTROLLER_LISTENER_NAMES', value: 'CONTROLLER' }
            // Single-node quorum talks to itself; ingress only publishes 9092.
            { name: 'KAFKA_CONTROLLER_QUORUM_VOTERS', value: '1@localhost:9093' }
            { name: 'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR', value: '1' }
            { name: 'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR', value: '1' }
            { name: 'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR', value: '1' }
            { name: 'KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS', value: '0' }
            { name: 'KAFKA_AUTO_CREATE_TOPICS_ENABLE', value: 'false' }
            { name: 'KAFKA_NUM_PARTITIONS', value: '3' }
            { name: 'KAFKA_LOG_DIRS', value: '/var/lib/kafka/data' }
          ]
          volumeMounts: [
            {
              volumeName: 'kafka-data'
              mountPath: '/var/lib/kafka/data'
            }
          ]
        }
        {
          // Sidecar rather than init container: topics can only be created once the
          // broker is accepting connections, which is after the main container starts.
          name: 'topic-init'
          image: '${acr.properties.loginServer}/pulseops-kafka-init:${imageTag}'
          command: ['/bin/sh', '-c']
          args: ['/scripts/init-topics.sh && sleep infinity']
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
          env: [
            // Containers in a replica share a network namespace.
            { name: 'KAFKA_BOOTSTRAP_SERVERS', value: 'localhost:9092' }
          ]
        }
      ]
      volumes: [
        {
          name: 'kafka-data'
          storageType: 'EmptyDir'
        }
      ]
      scale: {
        // A single broker with a fixed node id cannot be replicated.
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

// ---------------------------------------------------------------------------
// PulseOps platform
// ---------------------------------------------------------------------------

resource api 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'api'
  location: location
  dependsOn: [pgDb, pgFirewall]
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: registries
      secrets: [acrSecret, dbSecret]
      ingress: {
        // Reached through the web app's nginx proxy, so it needs no public endpoint.
        external: false
        targetPort: 8081
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'api'
          image: '${acr.properties.loginServer}/pulseops-api:${imageTag}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: concat(dbEnv, kafkaEnv)
        }
      ]
      scale: {
        // This app owns Flyway and hosts a Kafka consumer; one replica keeps
        // migration and consumption behaviour predictable.
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

resource worker 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'worker'
  location: location
  dependsOn: [pgDb, pgFirewall]
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: registries
      secrets: concat([acrSecret, dbSecret], llmSecrets)
      // Background consumer only: no ingress at all.
    }
    template: {
      containers: [
        {
          name: 'worker'
          image: '${acr.properties.loginServer}/pulseops-worker:${imageTag}'
          resources: {
            cpu: json('1.0')
            memory: '2Gi'
          }
          env: concat(dbEnv, kafkaEnv, llmEnv)
        }
      ]
      scale: {
        // Correlation state and alert windows are in-memory and single-instance.
        // More than one replica produces duplicate incidents.
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

resource web 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'web'
  location: location
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: registries
      secrets: [acrSecret]
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'web'
          image: '${acr.properties.loginServer}/pulseops-web:${imageTag}'
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
          env: [
            // nginx proxies /api here, keeping the browser on one origin.
            { name: 'API_UPSTREAM', value: 'http://api' }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 2
      }
    }
  }
}

// ---------------------------------------------------------------------------
// ShopFlow demo application
// ---------------------------------------------------------------------------

resource payment 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'payment'
  location: location
  dependsOn: [pgDb, pgFirewall]
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: registries
      secrets: [acrSecret, dbSecret]
      ingress: {
        // External so the failure-injection endpoint can drive the demo.
        // Restrict this with allowedClientIp: it can degrade the system on request.
        external: true
        targetPort: 8083
        transport: 'auto'
        ipSecurityRestrictions: demoIpRestrictions
      }
    }
    template: {
      containers: [
        {
          name: 'payment'
          image: '${acr.properties.loginServer}/pulseops-payment:${imageTag}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: concat(dbEnv, [
            { name: 'PULSEOPS_INGEST_BASE_URL', value: 'http://api' }
          ])
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

resource order 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'order'
  location: location
  dependsOn: [pgDb, pgFirewall]
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: registries
      secrets: [acrSecret, dbSecret]
      ingress: {
        external: true
        targetPort: 8082
        transport: 'auto'
        ipSecurityRestrictions: demoIpRestrictions
      }
    }
    template: {
      containers: [
        {
          name: 'order'
          image: '${acr.properties.loginServer}/pulseops-order:${imageTag}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: concat(dbEnv, [
            { name: 'PULSEOPS_INGEST_BASE_URL', value: 'http://api' }
            { name: 'SHOPFLOW_PAYMENT_BASE_URL', value: 'http://payment' }
          ])
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

output webUrl string = 'https://${web.properties.configuration.ingress.fqdn}'
output orderUrl string = 'https://${order.properties.configuration.ingress.fqdn}'
output paymentUrl string = 'https://${payment.properties.configuration.ingress.fqdn}'
output postgresHost string = pg.properties.fullyQualifiedDomainName
