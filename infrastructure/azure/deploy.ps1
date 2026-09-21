# Deploys PulseOps to Azure Container Apps.
#
# Two phases, because the container apps reference images that must already exist:
#   1. registry.bicep  -> ACR + managed identity
#   2. build and push  -> seven images
#   3. main.bicep      -> environment, Postgres, and the six apps
#
# Nothing here is destructive, but it does create billable resources.
# Review the cost notes in docs/architecture.md before running.

[CmdletBinding()]
param(
    [string] $ResourceGroup = 'pulseops-rg',
    [string] $Location = 'eastus',
    [string] $NamePrefix = 'pulseops',

    # Unique per deployment. Static tags cause stale-image pulls.
    [string] $Tag = (Get-Date -Format 'yyyyMMdd-HHmmss'),

    [Parameter(Mandatory)]
    [securestring] $PostgresPassword,

    # Leave unset to deploy with the deterministic offline RCA provider.
    [securestring] $LlmApiKey,

    # For Azure OpenAI use https://<resource>.openai.azure.com/openai/v1 and the
    # deployment name, which is not necessarily the model name.
    [string] $LlmBaseUrl = 'https://api.openai.com/v1',
    [string] $LlmModel = 'gpt-4.1-mini',
    [ValidateSet('bearer', 'api-key')]
    [string] $LlmAuthHeader = 'bearer',

    # Strongly recommended: restricts the ShopFlow demo endpoints, including the
    # failure-injection endpoint, to a single public IP.
    [string] $AllowedClientIp = '',

    # The docker.exe on PATH may be a broken standalone build.
    [string] $DockerExe = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Convert-Secret([securestring] $s) {
    if (-not $s) { return '' }
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

Write-Host "==> Resource group $ResourceGroup in $Location"
az group create --name $ResourceGroup --location $Location --output none

Write-Host '==> Phase 1: registry and identity'
$registry = az deployment group create `
    --resource-group $ResourceGroup `
    --template-file "$PSScriptRoot/registry.bicep" `
    --parameters namePrefix=$NamePrefix location=$Location `
    --query properties.outputs --output json | ConvertFrom-Json

$acrName = $registry.acrName.value
$loginServer = $registry.acrLoginServer.value
$identityName = $registry.identityName.value
Write-Host "    registry: $loginServer"

Write-Host '==> Phase 2: build and push images'
az acr login --name $acrName --output none

$builds = @(
    @{ Name = 'pulseops-api';     File = 'backend/pulseops-api/Dockerfile';              Context = '.' }
    @{ Name = 'pulseops-worker';  File = 'backend/pulseops-worker/Dockerfile';           Context = '.' }
    @{ Name = 'pulseops-order';   File = 'backend/shopflow-order-service/Dockerfile';    Context = '.' }
    @{ Name = 'pulseops-payment'; File = 'backend/shopflow-payment-service/Dockerfile';  Context = '.' }
    @{ Name = 'pulseops-web';     File = 'frontend/pulseops-web/Dockerfile';             Context = 'frontend/pulseops-web' }
    @{ Name = 'pulseops-kafka-init'; File = 'infrastructure/kafka/Dockerfile';           Context = 'infrastructure/kafka' }
)

Push-Location $repoRoot
try {
    foreach ($b in $builds) {
        $image = "$loginServer/$($b.Name):$Tag"
        Write-Host "    building $($b.Name)"
        & $DockerExe build -f $b.File -t $image $b.Context
        if ($LASTEXITCODE -ne 0) { throw "build failed: $($b.Name)" }
        & $DockerExe push $image
        if ($LASTEXITCODE -ne 0) { throw "push failed: $($b.Name)" }
    }

    # Mirrored into ACR rather than pulled from Docker Hub at run time, which is
    # rate limited and would fail replica starts unpredictably.
    Write-Host '    mirroring apache/kafka:3.8.0'
    & $DockerExe pull apache/kafka:3.8.0
    & $DockerExe tag apache/kafka:3.8.0 "$loginServer/pulseops-kafka:$Tag"
    & $DockerExe push "$loginServer/pulseops-kafka:$Tag"
    if ($LASTEXITCODE -ne 0) { throw 'push failed: pulseops-kafka' }
}
finally {
    Pop-Location
}

Write-Host '==> Phase 3: environment, database and apps'
$deployParams = @(
    "namePrefix=$NamePrefix"
    "location=$Location"
    "imageTag=$Tag"
    "acrName=$acrName"
    "identityName=$identityName"
    "postgresAdminPassword=$(Convert-Secret $PostgresPassword)"
    "allowedClientIp=$AllowedClientIp"
)
if ($LlmApiKey) {
    $deployParams += "llmApiKey=$(Convert-Secret $LlmApiKey)"
    $deployParams += "llmBaseUrl=$LlmBaseUrl"
    $deployParams += "llmModel=$LlmModel"
    $deployParams += "llmAuthHeader=$LlmAuthHeader"
}

$main = az deployment group create `
    --resource-group $ResourceGroup `
    --template-file "$PSScriptRoot/main.bicep" `
    --parameters $deployParams `
    --query properties.outputs --output json | ConvertFrom-Json

Write-Host ''
Write-Host 'Deployed.'
Write-Host "  dashboard : $($main.webUrl.value)"
Write-Host "  order API : $($main.orderUrl.value)/api/orders"
Write-Host "  payment   : $($main.paymentUrl.value)/internal/failure"
Write-Host ''
if (-not $AllowedClientIp) {
    Write-Warning 'The ShopFlow endpoints are open to the internet, including failure injection. Redeploy with -AllowedClientIp to restrict them.'
}
Write-Host 'The worker restarts until the API finishes its Flyway migration. This is expected on a first deployment.'
