<#
  Demonstracoes do Sprint 3 (Bully, heartbeat, historico e sincronizacao offline-first),
  em PowerShell puro - a convencao do roteiro (Windows + PowerShell), sem jq/curl/Python.

  Uso (na pasta iceibank/):
    powershell -ExecutionPolicy Bypass -File scripts\sprint3.ps1 <parte> [-Saida arquivo] [-Titulo "..."]

  Partes:
    subir          sobe as 3 agencias ao mesmo tempo (java -jar), sem limpar os logs
    parar [id]     derruba todas as agencias, ou so a de id informado (Stop-Process: abrupto,
                   como fechar o terminal da agencia)
    eleicao        limpa os logs, sobe as 3 juntas e consulta /bully/lider nas 3 (Parte A)
    escalonada     limpa os logs e sobe 0, 1 e 2 com 4 s de intervalo (tempestade com varias lideres)
    failover       derruba a lider atual, espera o heartbeat e consulta as que sobraram;
                   depois religa a derrubada (Parte B)
    historico      roda o HistoricoLideranca sobre os logs (Parte C)
    conflito       saldo com vetorAtual, deposito "enquanto o app estava offline",
                   sincronizacao recusada (409) e reenviada com o vetor novo (Parte E)
    lider          so consulta /bully/lider nas 3

  -Saida grava a saida em texto (UTF-8) e gera o PNG com scripts\gerar-prints.ps1 ao lado.

  Pre-requisitos: JDK 25 (JAVA_HOME, -JavaHome ou %LOCALAPPDATA%\AndroidDev\jdk-25*) e o jar
  em agencia\target (cd agencia; .\mvnw package -DskipTests). A RABBITMQ_URL vem do terminal
  ou da variavel de usuario do Windows; sem ela, as agencias sobem apontando para
  amqp://localhost (sem broker): Bully, contas e sincronizacao funcionam, e so a
  transferencia ENTRE agencias responde 503 (o debito e estornado).
#>
param(
  [Parameter(Position = 0)][string]$Parte = 'ajuda',
  [Parameter(Position = 1)][int]$Agencia = -1,
  [string]$Saida,
  [string]$Titulo,
  [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$RAIZ = Split-Path $PSScriptRoot -Parent
$DIR_AGENCIA = Join-Path $RAIZ 'agencia'
$JAR = Join-Path $DIR_AGENCIA 'target\iceibank-0.0.1-SNAPSHOT.jar'
$DADOS = Join-Path $DIR_AGENCIA 'data'
$OFFSET = if ($env:OFFSET) { [int]$env:OFFSET } else { 0 }
$script:linhas = New-Object System.Collections.Generic.List[string]

function Url([int]$id) { "http://localhost:$(4000 + $OFFSET + $id)" }

function Diga([string]$texto = '') {
  Write-Host $texto
  foreach ($l in ($texto -split "`r?`n")) {
    # Quebra linhas longas (JSON) depois de uma virgula, para o print nao ficar com 2000 px.
    while ($l.Length -gt 130) {
      $corte = $l.LastIndexOf(',', 130)
      if ($corte -lt 40) { break }
      $script:linhas.Add($l.Substring(0, $corte + 1))
      $l = '        ' + $l.Substring($corte + 1)
    }
    $script:linhas.Add($l)
  }
}

function Comando([string]$texto) { Diga "PS> $texto" }

function Json($objeto) { $objeto | ConvertTo-Json -Depth 8 -Compress }

function MostrarData {
  Comando 'Get-Date'
  Diga ((Get-Date | Out-String).Trim())
}

function Java {
  if (-not $JavaHome) {
    $candidato = Get-ChildItem "$env:LOCALAPPDATA\AndroidDev" -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidato) { $script:JavaHome = $candidato.FullName }
  }
  if ($JavaHome) { return (Join-Path $JavaHome 'bin\java.exe') }
  return 'java'
}

function NoAr([int]$id) {
  try { Invoke-RestMethod -Uri "$(Url $id)/status" -TimeoutSec 2 | Out-Null; return $true } catch { return $false }
}

function Esperar([scriptblock]$condicao, [string]$descricao, [int]$segundos = 90) {
  $fim = (Get-Date).AddSeconds($segundos)
  while ((Get-Date) -lt $fim) {
    if (& $condicao) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Tempo esgotado esperando: $descricao"
}

# Sem RABBITMQ_URL no terminal nem na variavel de usuario: le o .env.local do projeto (fora do Git).
function UrlDoEnvLocal {
  $arquivo = Join-Path $RAIZ '.env.local'
  if (-not (Test-Path $arquivo)) { return $null }
  $linha = Get-Content $arquivo | Where-Object { $_ -match '^RABBITMQ_URL=' } | Select-Object -First 1
  if ($linha) { return $linha.Substring('RABBITMQ_URL='.Length).Trim() }
  return $null
}

function RabbitUrl {
  $url = $env:RABBITMQ_URL
  if (-not $url) { $url = [Environment]::GetEnvironmentVariable('RABBITMQ_URL', 'User') }
  if (-not $url) { $url = UrlDoEnvLocal }
  if (-not $url) {
    Write-Warning 'RABBITMQ_URL nao definida: subindo com amqp://localhost (sem broker). Bully e contas funcionam; transferencia entre agencias responde 503.'
    $url = 'amqp://localhost:5672'
  }
  return $url
}

function LimparLogs {
  New-Item -ItemType Directory -Force -Path $DADOS | Out-Null
  $antigos = Get-ChildItem $DADOS -File | Where-Object { $_.Extension -in '.jsonl', '.out', '.err' }
  if (-not $antigos) { return }
  $copia = Join-Path $DADOS ('anteriores-' + (Get-Date -Format 'yyyy-MM-ddTHH-mm-ss'))
  New-Item -ItemType Directory -Path $copia | Out-Null
  $antigos | Move-Item -Destination $copia
}

function SubirProcesso([int]$id) {
  if (-not (Test-Path $JAR)) { throw "Jar nao encontrado: $JAR. Rode: cd agencia; .\mvnw package -DskipTests" }
  New-Item -ItemType Directory -Force -Path $DADOS | Out-Null
  # As variaveis vao pelo ambiente (herdado), nunca pela linha de comando: a RABBITMQ_URL
  # tem senha e apareceria na lista de processos.
  $env:AGENCIA_ID = "$id"
  $env:RABBITMQ_URL = RabbitUrl
  # cmd /c faz o redirecionamento para arquivo. Sem -RedirectStandard* o Start-Process usa
  # ShellExecute, e o java nao herda os handles deste PowerShell - senao quem chamou o
  # script (um pipe, um "*> arquivo") ficaria esperando ate as agencias serem derrubadas.
  $saida = Join-Path $DADOS "agencia-$id.out"
  $erros = Join-Path $DADOS "agencia-$id.err"
  $linha = "/c `"`"$(Java)`" -jar `"$JAR`" >> `"$saida`" 2>> `"$erros`"`""
  Start-Process -FilePath 'cmd.exe' -ArgumentList $linha -WorkingDirectory $DIR_AGENCIA -WindowStyle Hidden | Out-Null
}

function Subir([int[]]$ids) {
  foreach ($id in $ids) { if (NoAr $id) { throw "A agencia $id ja esta no ar." } }
  foreach ($id in $ids) { SubirProcesso $id }        # as 3 partem praticamente juntas
  foreach ($id in $ids) { Esperar { NoAr $id } "agencia $id responder em $(Url $id)" }
}

function PidDaAgencia([int]$id) {
  $conexao = Get-NetTCPConnection -LocalPort (4000 + $OFFSET + $id) -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($conexao) { return $conexao.OwningProcess }
  return $null
}

function Derrubar([int]$id) {
  $processo = PidDaAgencia $id
  if (-not $processo) { return }
  Stop-Process -Id $processo -Force
  Esperar { -not (NoAr $id) } "agencia $id sair do ar" 20
}

function ConsultarLider([int]$id) {
  Comando "Invoke-RestMethod -Uri `"$(Url $id)/bully/lider`" -Method Get"
  try {
    $r = Invoke-RestMethod -Uri "$(Url $id)/bully/lider" -Method Get -TimeoutSec 3
    $lider = if ($null -eq $r.liderAtual) { '(nenhuma)' } else { $r.liderAtual }
    Diga ("idAgencia : {0}   liderAtual : {1}   eleicaoEmAndamento : {2}" -f $r.idAgencia, $lider, $r.eleicaoEmAndamento)
    return $r.liderAtual
  } catch {
    Diga "Invoke-RestMethod : Impossivel conectar-se ao servidor remoto ($(Url $id)) - agencia fora do ar"
    return $null
  }
}

function LinhasDoConsole([int[]]$ids, [string]$padrao) {
  foreach ($id in $ids) {
    $arquivo = Join-Path $DADOS "agencia-$id.out"
    if (-not (Test-Path $arquivo)) { continue }
    Diga "--- console da agencia $id (agencia\data\agencia-$id.out) ---"
    Get-Content $arquivo | Where-Object { $_ -match $padrao } | ForEach-Object {
      Diga (($_ -replace '\s+RegistroEventos\s+:', ' :') -replace '^(\S+)\s+INFO\s+', '$1 ')
    }
  }
}

function ParteEleicao {
  Diga '=== Parte A - eleicao inicial: as 3 agencias sobem juntas ==='
  MostrarData
  LimparLogs
  Comando 'scripts\sprint3.ps1 subir    # java -jar ... com AGENCIA_ID=0, 1 e 2, ao mesmo tempo'
  Subir @(0, 1, 2)
  Diga 'As 3 agencias responderam em /status. Aguardando 6 s para a eleicao assentar...'
  Start-Sleep -Seconds 6
  Diga ''
  MostrarData
  $lideres = @(0, 1, 2) | ForEach-Object { ConsultarLider $_ }
  Diga ''
  $distintos = @($lideres | Sort-Object -Unique)
  if ($distintos.Count -eq 1 -and $null -ne $distintos[0]) {
    Diga ">>> As 3 agencias concordam: a lider e a agencia $($distintos[0])."
  } else {
    Diga ">>> As agencias ainda nao concordam: $($lideres -join ', ')"
  }
  Diga ''
  Diga '=== "Tempestade" da subida: TORNOU_LIDER / LIDER_ANUNCIADO nos logs de console ==='
  LinhasDoConsole @(0, 1, 2) 'TORNOU_LIDER|LIDER_ANUNCIADO|AGUARDANDO_COORDENADOR'
}

# Como abrir os 3 terminais um depois do outro: cada agencia que sobe ainda nao ve as de id
# maior, se declara lider e e "atropelada" pela proxima (pergunta 2 da Parte A).
function ParteEscalonada {
  Diga '=== Parte A (variacao) - subida escalonada: 0, depois 1, depois 2 (4 s de intervalo) ==='
  MostrarData
  LimparLogs
  foreach ($id in 0, 1, 2) {
    Comando "scripts\sprint3.ps1 subir $id"
    Subir @($id)
    Start-Sleep -Seconds 4
    MostrarData
    0..$id | ForEach-Object { ConsultarLider $_ } | Out-Null
    Diga ''
  }
  Diga '=== TORNOU_LIDER nos logs de console das 3 agencias ==='
  LinhasDoConsole @(0, 1, 2) 'TORNOU_LIDER'
}

function ParteFailover {
  Diga '=== Parte B - failover: derrubar a lider e deixar o heartbeat reagir ==='
  MostrarData
  $lideres = @(0, 1, 2) | ForEach-Object { ConsultarLider $_ }
  $lider = @($lideres | Where-Object { $null -ne $_ } | Sort-Object -Unique)[0]
  if ($null -eq $lider) { throw 'Nenhuma lider conhecida; rode a parte "eleicao" antes.' }
  Diga ''
  $processo = PidDaAgencia $lider
  Comando "Stop-Process -Id $processo -Force    # derruba a agencia $lider (a lider), como fechar o terminal dela"
  Derrubar $lider
  MostrarData
  $restantes = @(0, 1, 2) | Where-Object { $_ -ne $lider }
  Diga 'Ninguem mexe em nada: aguardando 8 s (heartbeat a cada 3 s + timeout de 1 s + eleicao)...'
  Start-Sleep -Seconds 8
  Diga ''
  MostrarData
  $novos = $restantes | ForEach-Object { ConsultarLider $_ }
  $distintos = @($novos | Sort-Object -Unique)
  Diga ''
  if ($distintos.Count -eq 1 -and $null -ne $distintos[0]) {
    Diga ">>> Sem intervencao manual, as agencias $($restantes -join ' e ') elegeram a agencia $($distintos[0])."
  } else {
    Diga ">>> Ainda sem consenso entre as restantes: $($novos -join ', ')"
  }
  Diga ''
  Diga '=== Deteccao da queda nos logs de console ==='
  LinhasDoConsole $restantes 'LIDER_NAO_RESPONDEU|TORNOU_LIDER|LIDER_ANUNCIADO'
  Diga ''
  Diga "=== Passo 6: religando a agencia $lider ==="
  Comando "scripts\sprint3.ps1 subir-uma $lider"
  Subir @($lider)
  Start-Sleep -Seconds 6
  MostrarData
  @(0, 1, 2) | ForEach-Object { ConsultarLider $_ } | Out-Null
}

function ParteHistorico {
  Diga '=== Parte C - historico de liderancas (HistoricoLideranca sobre agencia\data\*.jsonl) ==='
  MostrarData
  Comando 'cd agencia; .\mvnw -q exec:java@historico-lideranca'
  $java = Java
  if ($java -ne 'java') { $env:JAVA_HOME = Split-Path (Split-Path $java) }
  Push-Location $DIR_AGENCIA
  try {
    $ErrorActionPreference = 'Continue'
    $saida = & .\mvnw.cmd -q exec:java@historico-lideranca 2>&1 | ForEach-Object { "$_" } |
      Where-Object { $_ -notmatch '^WARNING' }
  } finally { Pop-Location }
  foreach ($l in $saida) { Diga "$l" }
}

# Invoke-WebRequest (e nao Invoke-RestMethod) para ter o status HTTP e o corpo cru da resposta,
# inclusive nos erros: no PowerShell 5.1 o corpo de um 409 so sai lendo o stream da excecao.
function Requisitar([string]$metodo, [string]$url, $corpo, [string]$token) {
  $cabecalhos = @{}
  if ($token) { $cabecalhos['Authorization'] = "Bearer $token" }
  $parametros = @{ Method = $metodo; Uri = $url; Headers = $cabecalhos; ContentType = 'application/json'; UseBasicParsing = $true }
  if ($null -ne $corpo) { $parametros['Body'] = (Json $corpo) }
  try {
    $r = Invoke-WebRequest @parametros
    $status = [int]$r.StatusCode
    $texto = $r.Content
  } catch {
    $status = 0
    $texto = ''
    $resposta = $_.Exception.Response
    if ($resposta) {
      $status = [int]$resposta.StatusCode
      $leitor = New-Object System.IO.StreamReader($resposta.GetResponseStream(), [System.Text.Encoding]::UTF8)
      $texto = $leitor.ReadToEnd()
    }
  }
  $obj = $null
  if ($texto) { try { $obj = $texto | ConvertFrom-Json } catch { $obj = $texto } }
  return [pscustomobject]@{ Status = $status; Corpo = $obj; Texto = $texto }
}

function ParteConflito {
  $base = Url 0
  Diga '=== Parte E - sincronizacao offline-first: deteccao de conflito via relogio vetorial ==='
  MostrarData
  Comando "`$login = Invoke-RestMethod -Method Post -Uri $base/auth/login -Body '{`"usuario`":`"ana`",`"senha`":`"***`"}'"
  $login = Requisitar 'POST' "$base/auth/login" @{ usuario = 'ana'; senha = 'ana123' } $null
  if ($login.Status -ne 200) { throw "Login falhou: $(Json $login.Corpo)" }
  $token = $login.Corpo.token
  Diga "token (JWT) obtido para $($login.Corpo.usuario), valido por $($login.Corpo.expiraEmSegundos) s"
  $r = Requisitar 'POST' "$base/contas" @{ id = 30; nomeAluno = 'Ana (Sprint 3)'; saldoInicial = 100 } $token
  Comando "Invoke-RestMethod -Method Post -Uri $base/contas -Body '{`"id`":30,`"saldoInicial`":100}'"
  Diga "HTTP $($r.Status) $($r.Texto)"
  Diga ''
  Diga '>>> 1. Consulta de saldo: agora a resposta traz o vetorAtual da agencia'
  Comando "Invoke-RestMethod -Uri $base/contas/30 -Headers @{Authorization=`"Bearer `$token`"}"
  $r = Requisitar 'GET' "$base/contas/30" $null $token
  Diga "HTTP $($r.Status) $($r.Texto)"
  $vetorAntigo = @($r.Corpo.vetorAtual)
  Comando "`$vetorAntigo = `$conta.vetorAtual    # = [$($vetorAntigo -join ',')] - o que o app guardaria"
  Diga ''
  Diga '>>> 2. Enquanto o "app" esta offline, o saldo muda por outro caminho (deposito direto)'
  Comando "Invoke-RestMethod -Method Post -Uri $base/contas/30/depositar -Body '{`"valor`":50}'"
  $r = Requisitar 'POST' "$base/contas/30/depositar" @{ valor = 50 } $token
  Diga "HTTP $($r.Status) $($r.Texto)"
  Diga ''
  Diga '>>> 3. O app reconecta e tenta sincronizar um saque de 30 feito offline, com o vetor ANTIGO'
  $pendente = @{ idOperacao = 'demo-' + [guid]::NewGuid().ToString().Substring(0, 8); tipo = 'SAQUE'; valor = 30; vetorConhecido = $vetorAntigo }
  Comando "Invoke-RestMethod -Method Post -Uri $base/contas/30/sincronizar -Body '$(Json $pendente)'"
  $r = Requisitar 'POST' "$base/contas/30/sincronizar" $pendente $token
  Diga "HTTP $($r.Status) $($r.Texto)"
  if ($r.Status -eq 409) { Diga '>>> 409: conflito detectado - a agencia NAO aplicou o saque as cegas.' }
  $vetorNovo = @($r.Corpo.vetorAtual)
  Diga ''
  Diga '>>> 4. Nova tentativa, agora com o vetorAtual devolvido na resposta de conflito'
  $pendente.vetorConhecido = $vetorNovo
  $pendente.idOperacao = 'demo-' + [guid]::NewGuid().ToString().Substring(0, 8)
  Comando "Invoke-RestMethod -Method Post -Uri $base/contas/30/sincronizar -Body '$(Json $pendente)'"
  $r = Requisitar 'POST' "$base/contas/30/sincronizar" $pendente $token
  Diga "HTTP $($r.Status) $($r.Texto)"
  if ($r.Status -eq 200) { Diga ">>> Aplicada: saldo final $($r.Corpo.conta.saldo) (100 + 50 - 30)." }
}

switch ($Parte) {
  'subir' { if ($Agencia -ge 0) { Subir @($Agencia) } else { Subir @(0, 1, 2) }; Diga 'Agencias no ar.' }
  'subir-uma' { Subir @($Agencia) }
  'parar' { if ($Agencia -ge 0) { Derrubar $Agencia } else { 0, 1, 2 | ForEach-Object { Derrubar $_ } }; Diga 'Agencias derrubadas.' }
  'eleicao' { ParteEleicao }
  'escalonada' { ParteEscalonada }
  'failover' { ParteFailover }
  'historico' { ParteHistorico }
  'conflito' { ParteConflito }
  'lider' { MostrarData; 0, 1, 2 | ForEach-Object { ConsultarLider $_ } | Out-Null }
  default { Get-Help $PSCommandPath -Detailed | Out-String | Write-Host; exit 0 }
}

if ($Saida) {
  $caminho = [IO.Path]::GetFullPath((Join-Path (Get-Location) $Saida))
  New-Item -ItemType Directory -Force -Path (Split-Path $caminho) | Out-Null
  [IO.File]::WriteAllLines($caminho, $script:linhas, (New-Object System.Text.UTF8Encoding $false))
  $png = Join-Path (Split-Path (Split-Path $caminho)) ([IO.Path]::GetFileNameWithoutExtension($caminho) + '.png')
  if (-not $Titulo) { $Titulo = "Windows PowerShell - ICEIBank Sprint 3 - $Parte" }
  & (Join-Path $PSScriptRoot 'gerar-prints.ps1') $caminho $png $Titulo
  Write-Host "Print gerado: $png"
}
