<#
.SYNOPSIS
    Re-dates the seeded demo evidence relative to now.

.DESCRIPTION
    V9__demo_seed.sql dates deployments and pull requests relative to MIGRATION time,
    so the evidence is only recent on a freshly migrated database. Once the database
    is more than a day old the seeded deployment falls outside the investigation's
    24-hour lookback, and the AI silently stops citing deployments, PRs and code.

    Run this before a demo on a long-lived database. Offsets match V9 exactly.

.EXAMPLE
    .\scripts\refresh-demo-data.ps1
#>
[CmdletBinding()]
param(
    [string]$Container = 'pulseops-postgres',
    [string]$Database = 'pulseops',
    [string]$User = 'pulseops',
    # The docker.exe on PATH may be a broken standalone build.
    [string]$DockerExe = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
)

$sql = @'
UPDATE deployments SET deployed_at = now() - INTERVAL '30 minutes' WHERE service_name = 'payment-service' AND version = '1.4.2';
UPDATE deployments SET deployed_at = now() - INTERVAL '6 days'     WHERE service_name = 'payment-service' AND version = '1.4.1';
UPDATE deployments SET deployed_at = now() - INTERVAL '4 days'     WHERE service_name = 'order-service'   AND version = '2.1.0';

UPDATE pull_requests SET merged_at = now() - INTERVAL '45 minutes' WHERE number = 482 AND repository = 'shopflow';
UPDATE pull_requests SET merged_at = now() - INTERVAL '6 days'     WHERE number = 475 AND repository = 'shopflow';
UPDATE pull_requests SET merged_at = now() - INTERVAL '4 days'     WHERE number = 469 AND repository = 'shopflow';

SELECT service_name, version, date_trunc('second', now() - deployed_at) AS age FROM deployments ORDER BY deployed_at DESC;
'@

& $DockerExe exec -i $Container psql -U $User -d $Database -v ON_ERROR_STOP=1 -c $sql
if ($LASTEXITCODE -ne 0) { throw 'Refresh failed. Is the postgres container running?' }

Write-Host 'Demo evidence re-dated. The next investigation will cite deployments, PRs and code again.' -ForegroundColor Green
