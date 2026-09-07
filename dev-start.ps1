$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$infra = Join-Path $root "infrastructure"
$backend = Join-Path $root "backend"
$envFile = Join-Path $infra ".env"
$composeFile = Join-Path $infra "compose.yaml"

Write-Host ""
Write-Host "=== VallaBank Development Start ==="
Write-Host ""

# --------------------------------------------------
# 1. Controllo Docker
# --------------------------------------------------

Write-Host "[1/4] Controllo Docker..."

docker info *> $null

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker non risulta disponibile."
    Write-Host "Avvia Docker Desktop e riprova."
    exit 1
}

Write-Host "Docker disponibile."

# --------------------------------------------------
# 2. Caricamento variabili da infrastructure/.env
# --------------------------------------------------

Write-Host "[2/4] Caricamento variabili ambiente..."

if (-not (Test-Path $envFile)) {
    Write-Host "File .env non trovato:"
    Write-Host $envFile
    exit 1
}

Get-Content $envFile |
    Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } |
    ForEach-Object {
        $name, $value = $_ -split '=', 2

        $value = $value.Trim().Trim('"').Trim("'")

        Set-Item -Path "Env:$name" -Value $value
    }

Write-Host "Database: $env:POSTGRES_DB"
Write-Host "Utente DB: $env:POSTGRES_USER"
Write-Host "Variabili caricate."

# --------------------------------------------------
# 3. Avvio PostgreSQL
# --------------------------------------------------

Write-Host "[3/4] Avvio PostgreSQL..."

docker compose `
    --env-file $envFile `
    -f $composeFile `
    up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "Errore durante l'avvio di PostgreSQL."
    exit 1
}

Write-Host "Attendo che PostgreSQL sia pronto..."

$databaseReady = $false

for ($i = 1; $i -le 30; $i++) {

    docker compose `
        --env-file $envFile `
        -f $composeFile `
        exec -T postgres `
        pg_isready `
        -U $env:POSTGRES_USER `
        -d $env:POSTGRES_DB *> $null

    if ($LASTEXITCODE -eq 0) {
        $databaseReady = $true
        break
    }

    Start-Sleep -Seconds 1
}

if (-not $databaseReady) {
    Write-Host "PostgreSQL non è diventato disponibile."
    exit 1
}

Write-Host "PostgreSQL pronto."

# --------------------------------------------------
# 4. Avvio Spring Boot
# --------------------------------------------------

Write-Host "[4/4] Avvio Spring Boot..."
Write-Host ""
Write-Host "Backend: http://localhost:8080"
Write-Host "Premi Ctrl+C per fermare Spring Boot."
Write-Host ""

Push-Location $backend

try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}