<#
.SYNOPSIS
    Turns the demo payment outage on or off.

.EXAMPLE
    .\scripts\inject-failure.ps1 -ErrorRate 50 -LatencyMs 2000
    .\scripts\inject-failure.ps1 -Reset
#>
[CmdletBinding()]
param(
    [string]$PaymentServiceUrl = 'http://localhost:8083',
    [ValidateRange(0, 100)][int]$ErrorRate = 50,
    [ValidateRange(0, 60000)][int]$LatencyMs = 2000,
    [switch]$Reset
)

if ($Reset) {
    $result = Invoke-RestMethod -Uri "$PaymentServiceUrl/internal/failure/reset" -Method Post
    Write-Host 'Failure injection reset.' -ForegroundColor Green
}
else {
    $body = @{ errorRate = $ErrorRate; latencyMs = $LatencyMs } | ConvertTo-Json -Compress
    $result = Invoke-RestMethod -Uri "$PaymentServiceUrl/internal/failure" -Method Post `
        -ContentType 'application/json' -Body $body
    Write-Host "Failure injection enabled: ${ErrorRate}% errors, +${LatencyMs}ms latency." -ForegroundColor Yellow
}

$result | ConvertTo-Json
