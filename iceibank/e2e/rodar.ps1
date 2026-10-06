# Roda os testes gravados do Sprint 2.
#   .\e2e\rodar.ps1                         usa o JAVA_HOME atual
#   .\e2e\rodar.ps1 -JavaHome C:\jdk-25     informa o JDK 25
# A RABBITMQ_URL vem do terminal ou, se nao houver, da variavel de usuario do Windows.
param([string]$JavaHome = $env:JAVA_HOME)

$url = $env:RABBITMQ_URL
if (-not $url) { $url = [Environment]::GetEnvironmentVariable('RABBITMQ_URL', 'User') }
if (-not $url) {
    Write-Error "Defina a RABBITMQ_URL: [Environment]::SetEnvironmentVariable('RABBITMQ_URL','amqps://...','User')"
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
