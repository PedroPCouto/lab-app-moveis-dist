param([string]$JavaHome = $env:JAVA_HOME, [switch]$Compilar)

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
