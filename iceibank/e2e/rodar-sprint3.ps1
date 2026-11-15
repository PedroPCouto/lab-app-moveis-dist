# Roda os testes gravados do Sprint 3 (app Flutter + agencias reais).
#   .\e2e\rodar-sprint3.ps1                         usa JAVA_HOME ou %LOCALAPPDATA%\AndroidDev\jdk-25*
#   .\e2e\rodar-sprint3.ps1 -JavaHome C:\jdk-25     informa o JDK 25
#   .\e2e\rodar-sprint3.ps1 -Compilar               recompila o build web do app antes
# Pre-requisitos: jar em agencia\target e build web em app_mobile\build\web
# (flutter build web --release --no-web-resources-cdn). Sem RABBITMQ_URL, as agencias sobem
# sem broker (a transferencia entre agencias responde 503 - o resto funciona).
param([string]$JavaHome = $env:JAVA_HOME, [switch]$Compilar)

# Sem RABBITMQ_URL no terminal nem na variavel de usuario: le o .env.local do projeto (fora do Git).
function UrlDoEnvLocal {
  $arquivo = Join-Path (Split-Path $PSScriptRoot -Parent) '.env.local'
  if (-not (Test-Path $arquivo)) { return $null }
  $linha = Get-Content $arquivo | Where-Object { $_ -match '^RABBITMQ_URL=' } | Select-Object -First 1
  if ($linha) { return $linha.Substring('RABBITMQ_URL='.Length).Trim() }
  return $null
}

if (-not $JavaHome) {
  $jdk = Get-ChildItem "$env:LOCALAPPDATA\AndroidDev" -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($jdk) { $JavaHome = $jdk.FullName }
}
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
$url = $env:RABBITMQ_URL
if (-not $url) { $url = [Environment]::GetEnvironmentVariable('RABBITMQ_URL', 'User') }
if (-not $url) { $url = UrlDoEnvLocal }
if ($url) { $env:RABBITMQ_URL = $url }

$raiz = Split-Path $PSScriptRoot -Parent
if ($Compilar) {
  Push-Location (Join-Path $raiz 'app_mobile')
  try { flutter build web --release --no-web-resources-cdn } finally { Pop-Location }
}

Push-Location $PSScriptRoot
try {
  if (-not (Test-Path node_modules)) { npm install; npx playwright install chromium }
  npx playwright test --config playwright.sprint3.config.js
  $codigo = $LASTEXITCODE
} finally {
  Pop-Location
}
exit $codigo
