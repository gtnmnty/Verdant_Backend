# Rehearse the Flyway migrations on a restored copy of production, without touching Supabase.
#
#   .\scripts\rehearse-migration.ps1 -Dump ..\verdant_prod.dump
#
# 1. drops and recreates the local database "verdant_rehearsal" and restores the dump into it
# 2. runs the V12 email-collision pre-check on the restored data
# 3. starts the backend on port 8089 against that copy, waits until Flyway and Hibernate validation pass
# 4. prints the Flyway history, then stops the app
# Scheduled jobs are switched off so nothing calls Stripe/Cloudinary with the real keys from .env.local.
param(
    [Parameter(Mandatory)] [string] $Dump,
    [string] $Database = "verdant_rehearsal"
)
$ErrorActionPreference = "Continue"
$bin = "C:\Program Files\PostgreSQL\18\bin"
$backend = Split-Path $PSScriptRoot -Parent

# Same variables IntelliJ gets from .env.local (JWT, Stripe, ... only need to exist, nothing is called).
Get-Content "$backend\.env.local" | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    Set-Item "env:$k" $v.Trim()
}
$env:PGPASSWORD = $env:DB_PASSWORD
$env:PGOPTIONS = "-c client_min_messages=warning"   # no NOTICE lines (PowerShell 5.1 turns stderr into errors)
$pg = @("-h", "localhost", "-U", "postgres")

& "$bin\psql.exe" @pg -qc "DROP DATABASE IF EXISTS $Database" -c "CREATE DATABASE $Database"
# The dump is public-schema only, so the extension the overlap constraint needs must exist first.
# Restore errors about Supabase-only row-level-security policies (auth.uid()) are expected and hidden.
& "$bin\psql.exe" @pg -d $Database -qc "CREATE EXTENSION IF NOT EXISTS btree_gist"
& "$bin\pg_restore.exe" @pg -d $Database --no-owner --no-acl (Resolve-Path $Dump) 2>$null
Write-Host "Restored $Dump into $Database (Supabase-only policy errors ignored)."

Write-Host "`n== V12 pre-check: emails that collide ignoring case/spaces (expect none)"
& "$bin\psql.exe" @pg -d $Database -c "SELECT lower(trim(email)) AS email, count(*) FROM users GROUP BY 1 HAVING count(*) > 1"

$env:DB_URL = "jdbc:postgresql://localhost:5432/$Database"
$env:DB_USERNAME = "postgres"
$env:SERVER_PORT = "8089"
$env:APP_SCHEDULING_ENABLED = "false"
$env:SPRING_PROFILES_ACTIVE = "local"

$log = "$env:TEMP\rehearsal-app.log"
Remove-Item $log -ErrorAction SilentlyContinue
Write-Host "`n== Starting the backend (log: $log)"
$app = Start-Process -FilePath "$backend\mvnw.cmd" -ArgumentList "-q", "spring-boot:run" -WorkingDirectory $backend `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err" -PassThru -WindowStyle Hidden

$deadline = (Get-Date).AddMinutes(4)
$result = "timeout"
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    $text = (Get-Content $log, "$log.err" -ErrorAction SilentlyContinue) -join "`n"
    if ($text -match "Started SalonEcommApplication") { $result = "started"; break }
    if ($text -match "APPLICATION FAILED TO START|Migration .* failed|Schema-validation") { $result = "failed"; break }
    if ($app.HasExited) { $result = "failed"; break }
}

# Stop only what this script started: the maven process tree, plus whatever listens on 8089.
# (Never match on the app name: that would also kill a copy running from IntelliJ.)
taskkill /PID $app.Id /T /F 2>$null | Out-Null
Get-NetTCPConnection -LocalPort 8089 -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }

Write-Host "`n== Flyway history on $Database"
& "$bin\psql.exe" @pg -d $Database -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"

if ($result -eq "started") { Write-Host "`nRESULT: OK - migrations applied and the schema validates." -ForegroundColor Green }
else {
    Write-Host "`nRESULT: $result - last log lines:" -ForegroundColor Red
    Get-Content $log, "$log.err" -ErrorAction SilentlyContinue | Where-Object { $_ -match "ERROR|Migration|Caused by|Schema-validation" } | Select-Object -Last 12
    exit 1
}
