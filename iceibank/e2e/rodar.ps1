# Roda os testes gravados do Sprint 2.
#   .\e2e\rodar.ps1                         usa o JAVA_HOME atual
#   .\e2e\rodar.ps1 -JavaHome C:\jdk-25     informa o JDK 25
# A RABBITMQ_URL vem do terminal, da variavel de usuario do Windows ou do iceibank.env.local.
param([string]$JavaHome = $env:JAVA_HOME)

# Sem RABBITMQ_URL no terminal nem na variavel de usuario: le o .env.local do projeto (fora do Git).
function UrlDoEnvLocal {
  $arquivo = Join-Path (Split-Path $PSScriptRoot -Parent) '.env.local'
  if (-not (Test-Path $arquivo)) { return $null }
  $linha = Get-Content $arquivo | Where-Object { $_ -match '^RABBITMQ_URL=' } | Select-Object -First 1
  if ($linha) { return $linha.Substring('RABBITMQ_URL='.Length).Trim() }
  return $null
}

$url = $env:RABBITMQ_URL
if (-not $url) { $url = [Environment]::GetEnvironmentVariable('RABBITMQ_URL', 'User') }
if (-not $url) { $url = UrlDoEnvLocal }
if (-not $url) {
    Write-Error "Defina a RABBITMQ_URL no iceibank.env.local (modelo: .env.exemplo) ou na variavel de usuario."
    exit 1
}
$env:RABBITMQ_URL = $url
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }

Push-Location $PSScriptRoot
try {
    if (-not (Test-Path node_modules)) { npm install; npx playwright install chromium }
    npx playwright test
    $codigo = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $codigo
