// Phase 1 of the deployment: the registry and the identity used to pull from it.
// Separate from main.bicep because images must be pushed before the container apps
// that reference them can be created.

targetScope = 'resourceGroup'

param location string = resourceGroup().location
param namePrefix string = 'pulseops'

var acrName = toLower('${namePrefix}acr${uniqueString(resourceGroup().id)}')
var acrPullRoleId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: { name: 'Basic' }
  properties: {
    // Images are pulled with a managed identity, so the admin account stays off.
    adminUserEnabled: false
  }
}

resource uami 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${namePrefix}-identity'
  location: location
}

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, uami.id, acrPullRoleId)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
    principalId: uami.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

output acrName string = acr.name
output acrLoginServer string = acr.properties.loginServer
output identityName string = uami.name
