<#
.SYNOPSIS
    Loads .env into the current PowerShell session.

.DESCRIPTION
    Keeps secrets in a single gitignored file instead of scattered across shell history,
    launch configs, or source. Run this once per terminal, then start the services normally.

    .env is in .gitignore. Never commit it, paste it into chat, or screenshot it.

.EXAMPLE
    . .\scripts\load-env.ps1
    .\mvnw.cmd -pl backend/pulseops-worker spring-boot:run
#>
[CmdletBinding()]
param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env')
)

if (-not (Test-Path $Path)) {
    Write-Host "No .env found at $Path" -ForegroundColor Yellow
    Write-Host "Create one with:  Copy-Item .env.example .env" -ForegroundColor Yellow
    return
}

$loaded = @()
foreach ($line in Get-Content $Path) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) {
        continue
    }

    $separator = $trimmed.IndexOf('=')
    if ($separator -lt 1) {
        continue
    }

    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim().Trim('"').Trim("'")

    Set-Item -Path "Env:$name" -Value $value
    $loaded += $name
}

Write-Host "Loaded $($loaded.Count) variable(s) into this session." -ForegroundColor Green

# Names only. Values are never echoed, so this is safe to run while screen sharing.
$loaded | ForEach-Object {
    $isSecret = $_ -match 'KEY|PASSWORD|SECRET|TOKEN'
    $shown = if ($isSecret) { '***' } else { [Environment]::GetEnvironmentVariable($_) }
    Write-Host ("  {0,-28} {1}" -f $_, $shown)
}
