param([string]$JavaHome = $env:JAVA_HOME)

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
    Write-Error "Defina a RABBITMQ_URL no iceibank\.env.local (modelo: .env.exemplo) ou na variavel de usuario."
    exit 1
}
$env:RABBITMQ_URL = $url
if (-not $JavaHome) {
    $jdk = Get-ChildItem "$env:LOCALAPPDATA\AndroidDev" -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $JavaHome = $jdk.FullName }
}
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
