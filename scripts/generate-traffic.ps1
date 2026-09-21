<#
.SYNOPSIS
    Generates steady order traffic against ShopFlow.

.DESCRIPTION
    Produces the background load the PulseOps demo needs. Without continuous traffic the Alert
    Engine's sliding window has nothing to measure, so "healthy" and "degraded" look identical.

.EXAMPLE
    .\scripts\generate-traffic.ps1 -DurationSeconds 300 -RequestsPerSecond 5
#>
[CmdletBinding()]
param(
    [string]$OrderServiceUrl = 'http://localhost:8082',
    [int]$DurationSeconds = 120,
    [double]$RequestsPerSecond = 4,
    [switch]$Quiet
)

$ErrorActionPreference = 'Continue'
$delayMs = [int](1000 / $RequestsPerSecond)
$deadline = (Get-Date).AddSeconds($DurationSeconds)

$succeeded = 0
$failed = 0
$latencies = [System.Collections.Generic.List[double]]::new()

Write-Host "Generating traffic against $OrderServiceUrl for ${DurationSeconds}s at ~${RequestsPerSecond} rps..." -ForegroundColor Cyan

while ((Get-Date) -lt $deadline) {
    $body = @{
        customerId = "cust-{0:D4}" -f (Get-Random -Minimum 1 -Maximum 500)
        amount     = [math]::Round((Get-Random -Minimum 5.0 -Maximum 250.0), 2)
        currency   = 'USD'
    } | ConvertTo-Json -Compress

    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $null = Invoke-WebRequest -Uri "$OrderServiceUrl/api/orders" -Method Post `
            -ContentType 'application/json' -Body $body -UseBasicParsing -ErrorAction Stop
        $sw.Stop()
        $succeeded++
        $status = 201
    }
    catch {
        $sw.Stop()
        $failed++
        $status = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    }

    $latencies.Add($sw.Elapsed.TotalMilliseconds)
    if (-not $Quiet) {
        $colour = if ($status -eq 201) { 'DarkGray' } else { 'Red' }
        Write-Host ("  HTTP {0}  {1,6:N0} ms" -f $status, $sw.Elapsed.TotalMilliseconds) -ForegroundColor $colour
    }

    Start-Sleep -Milliseconds $delayMs
}

$total = $succeeded + $failed
$sorted = $latencies | Sort-Object
$p95 = if ($sorted.Count -gt 0) { $sorted[[math]::Min($sorted.Count - 1, [int]($sorted.Count * 0.95))] } else { 0 }

Write-Host ''
Write-Host 'Summary' -ForegroundColor Cyan
Write-Host ("  requests : {0}" -f $total)
Write-Host ("  succeeded: {0}" -f $succeeded)
Write-Host ("  failed   : {0} ({1:P1})" -f $failed, $(if ($total) { $failed / $total } else { 0 }))
Write-Host ("  p95      : {0:N0} ms" -f $p95)
