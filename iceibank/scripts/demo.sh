#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/comum.sh"

ANA=""; BRUNO=""; CARLA=""

fazer_login() {
  ANA=$(obter_token ana ana123 0)
  BRUNO=$(obter_token bruno bruno123 1)
  CARLA=$(obter_token carla carla123 2)
  [ -z "$ANA" ] && { echo "Nao consegui autenticar - as agencias estao no ar?"; exit 1; }
}

parte_preparar() {
  titulo "PREPARACAO - contas de exemplo (particao: conta N mora na agencia N % 3)"
  fazer_login
  passo "Conta 0 -> agencia 0 (dona: ana)"
  chamar POST "$(url_da_agencia 0)/contas" "$ANA" '{"id":0,"nomeAluno":"Ana","saldoInicial":200}'
  passo "Conta 3 -> agencia 0 tambem, porque 3 % 3 == 0 (dona: ana)"
  chamar POST "$(url_da_agencia 0)/contas" "$ANA" '{"id":3,"nomeAluno":"Ana (2a conta)","saldoInicial":50}'
  passo "Conta 1 -> agencia 1 (dono: bruno)"
  chamar POST "$(url_da_agencia 1)/contas" "$BRUNO" '{"id":1,"nomeAluno":"Bruno","saldoInicial":80}'
  passo "Conta 2 -> agencia 2 (dona: carla)"
  chamar POST "$(url_da_agencia 2)/contas" "$CARLA" '{"id":2,"nomeAluno":"Carla","saldoInicial":120}'
  passo "Conta 1 na agencia 0: recusada, nao e a particao dela"
  chamar POST "$(url_da_agencia 0)/contas" "$ANA" '{"id":1,"nomeAluno":"X","saldoInicial":10}'

  passo "Eventos concorrentes nas 3 agencias (para gerar empates de Lamport)"
  for i in 1 2 3; do
    curl -s -o /dev/null -X POST "$(url_da_agencia 0)/contas/0/depositar" \
      -H "Authorization: Bearer $ANA" -H 'Content-Type: application/json' -d '{"valor":1}' &
    curl -s -o /dev/null -X POST "$(url_da_agencia 1)/contas/1/depositar" \
      -H "Authorization: Bearer $BRUNO" -H 'Content-Type: application/json' -d '{"valor":1}' &
    curl -s -o /dev/null -X POST "$(url_da_agencia 2)/contas/2/depositar" \
      -H "Authorization: Bearer $CARLA" -H 'Content-Type: application/json' -d '{"valor":1}' &
    wait
  done
  echo "  9 depositos disparados (3 por agencia, praticamente ao mesmo tempo)."
}

parte_local() {
  titulo "PARTE D - TRANSFERENCIA LOCAL (conta 0 -> conta 3, ambas na agencia 0)"
  fazer_login
  passo "Saldos antes"
  chamar GET "$(url_da_agencia 0)/contas" "$ANA"
  passo "Transferindo R$ 40,00"
  chamar POST "$(url_da_agencia 0)/transferencias" "$ANA" '{"idOrigem":0,"idDestino":3,"valor":40}'
  passo "Saldos depois"
  chamar GET "$(url_da_agencia 0)/contas" "$ANA"
  passo "Eventos gravados pela agencia 0 (dois eventos LOCAIS do relogio de Lamport)"
  tail -2 "$AGENCIA_DIR/data/eventos-agencia-0.jsonl" | jq -c '{tipo, timestampLamport, detalhes}'
}

parte_entre_agencias() {
  titulo "PARTE D - TRANSFERENCIA ENTRE AGENCIAS (conta 0 na ag.0 -> conta 1 na ag.1)"
  fazer_login
  passo "Relogio de Lamport de cada agencia ANTES"
  for id in 0 1; do curl -s "$(url_da_agencia $id)/status" | jq -c '{agencia:.idAgencia, lamport:.relogioLamport}'; done
  passo "Transferindo R$ 30,00 da conta 0 para a conta 1"
  chamar POST "$(url_da_agencia 0)/transferencias" "$ANA" '{"idOrigem":0,"idDestino":1,"valor":30}'
  passo "Saldo na agencia de origem (conta 0)"
  chamar GET "$(url_da_agencia 0)/contas/0" "$ANA"
  passo "Saldo na agencia de destino (conta 1)"
  chamar GET "$(url_da_agencia 1)/contas/1" "$BRUNO"
  passo "Relogio de Lamport de cada agencia DEPOIS (regra 3: max(local, recebido) + 1)"
  for id in 0 1; do curl -s "$(url_da_agencia $id)/status" | jq -c '{agencia:.idAgencia, lamport:.relogioLamport}'; done
  passo "Log da agencia 0 (lado do ENVIO)"
  grep TRANSFERENCIA_DEBITO "$AGENCIA_DIR/data/eventos-agencia-0.jsonl" | tail -1 | jq -c .
  passo "Log da agencia 1 (lado do RECEBIMENTO)"
  grep TRANSFERENCIA_CREDITO_REMOTO "$AGENCIA_DIR/data/eventos-agencia-1.jsonl" | tail -1 | jq -c .
}

parte_falha() {
  titulo "PARTE D - FALHA CONHECIDA (agencia de destino fora do ar)"
  fazer_login
  passo "Saldo da conta 0 ANTES"
  chamar GET "$(url_da_agencia 0)/contas/0" "$ANA"
  passo "Derrubando a agencia 1, que e a dona da conta de destino"
  "$RAIZ/scripts/parar-agencias.sh" 1
  sleep 2
  curl -s -o /dev/null -w "  GET agencia 1 /status -> HTTP %{http_code}  (000 = fora do ar)\n" \
    "$(url_da_agencia 1)/status"
  passo "Tentando transferir R\$ 10,00 da conta 0 para a conta 1"
  chamar POST "$(url_da_agencia 0)/transferencias" "$ANA" '{"idOrigem":0,"idDestino":1,"valor":10}'
  passo "Saldo da conta 0 DEPOIS do erro"
  chamar GET "$(url_da_agencia 0)/contas/0" "$ANA"
  echo
  echo "  >>> O debito NAO foi revertido: o dinheiro sumiu temporariamente."
  echo "  >>> Limitacao conhecida e proposital deste sprint (Sprint 4 resolve com 2PC/Saga)."
  passo "Inconsistencia registrada no log da agencia 0"
  grep TRANSFERENCIA_FALHOU "$AGENCIA_DIR/data/eventos-agencia-0.jsonl" | tail -1 | jq .
}

parte_linha_do_tempo() {
  titulo "PARTE E - LINHA DO TEMPO UNIFICADA"
  (cd "$AGENCIA_DIR" && ./mvnw -q exec:java@mesclar-logs 2>/dev/null)
}

parte_auth_sem_token() {
  titulo "PARTE F - CENARIO (a): requisicao SEM token"
  passo "GET /contas/0 sem o cabecalho Authorization"
  chamar GET "$(url_da_agencia 0)/contas/0"
  passo "POST /contas/0/sacar sem token"
  chamar POST "$(url_da_agencia 0)/contas/0/sacar" "" '{"valor":10}'
  passo "POST /transferencias sem token"
  chamar POST "$(url_da_agencia 0)/transferencias" "" '{"idOrigem":0,"idDestino":1,"valor":10}'
  echo
  echo "  >>> Toda rota que le ou modifica contas responde 401 sem token."
  passo "A rota publica de health-check continua acessivel (nao mexe em conta)"
  chamar GET "$(url_da_agencia 0)/status"
}

parte_auth_com_token() {
  titulo "PARTE F - CENARIO (b): requisicao COM token valido"
  passo "POST /auth/login"
  chamar POST "$(url_da_agencia 0)/auth/login" "" '{"usuario":"ana","senha":"ana123"}'
  local token; token=$(obter_token ana ana123 0)
  passo "Conteudo do token (payload em base64url, sem a assinatura)"
  echo "$token" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .
  passo "GET /contas/0 com o token"
  chamar GET "$(url_da_agencia 0)/contas/0" "$token"
  passo "POST /contas/0/depositar com o token"
  chamar POST "$(url_da_agencia 0)/contas/0/depositar" "$token" '{"valor":10}'
  passo "Senha errada nao emite token"
  chamar POST "$(url_da_agencia 0)/auth/login" "" '{"usuario":"ana","senha":"errada"}'
}

parte_auth_token_expirado() {
  titulo "PARTE F - CENARIO (c): requisicao com token EXPIRADO"
  passo "Gerando um token assinado corretamente, porem ja vencido"
  local expirado; expirado=$(cd "$AGENCIA_DIR" && ./mvnw -q exec:java@token-expirado 2>/dev/null | tail -1)
  echo "  token: ${expirado:0:60}..."
  passo "Payload do token (repare no exp anterior ao iat da execucao atual)"
  echo "$expirado" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss, sub, tipo, iat, exp}'
  echo "  agora (epoch): $(date +%s)"
  passo "GET /contas/0 com o token expirado"
  chamar GET "$(url_da_agencia 0)/contas/0" "$expirado"
  passo "Token adulterado (assinatura nao confere) - tambem 401"
  chamar GET "$(url_da_agencia 0)/contas/0" "$(obter_token ana ana123 0)xyz"
  passo "Token de pessoa na rota interna entre agencias - 403, nao 401"
  chamar POST "$(url_da_agencia 0)/contas/0/creditar-remoto" "$(obter_token ana ana123 0)" \
    '{"valor":10,"timestampLamport":1,"origemAgencia":1}'
}

parte_auth() {
  parte_auth_sem_token
  parte_auth_com_token
  parte_auth_token_expirado
}

parte_extra() {
  titulo "FUNCIONALIDADE ADICIONAL - historico por conta e status por agencia"
  fazer_login
  passo "GET /contas/0/historico?limite=6  (eventos da conta, do mais recente ao mais antigo)"
  curl -s "$(url_da_agencia 0)/contas/0/historico?limite=6" -H "Authorization: Bearer $ANA" \
    | jq '{idConta, agencia, total, eventos: [.eventos[] | {timestampLamport, tipo, detalhes}]}'
  passo "GET /status de cada agencia (rota publica: relogio de Lamport e contas da particao)"
  for id in $(seq 0 $((NUMERO_AGENCIAS - 1))); do
    curl -s "$(url_da_agencia $id)/status" | jq -c . || echo "  agencia $id fora do ar"
  done
  passo "O historico tambem exige autorizacao: bruno nao ve a conta da ana"
  chamar GET "$(url_da_agencia 0)/contas/0/historico" "$(obter_token bruno bruno123 0)"
}

echo "Data/hora desta execucao: $(date)"
echo "Maquina: $(hostname)   Usuario: $(whoami)"

case "${1:-tudo}" in
  preparar)       parte_preparar ;;
  local)          parte_local ;;
  entre-agencias) parte_entre_agencias ;;
  falha)          parte_falha ;;
  linha-do-tempo) parte_linha_do_tempo ;;
  auth)                parte_auth ;;
  auth-sem-token)      parte_auth_sem_token ;;
  auth-com-token)      parte_auth_com_token ;;
  auth-token-expirado) parte_auth_token_expirado ;;
  extra)          parte_extra ;;
  tudo)
    parte_preparar; parte_local; parte_entre_agencias; parte_extra
    parte_auth; parte_falha; parte_linha_do_tempo ;;
  *) echo "Parte desconhecida: $1"; exit 1 ;;
esac
