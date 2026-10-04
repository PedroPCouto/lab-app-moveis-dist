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
}

parte_concorrentes() {
  titulo "PARTE D - OPERACOES INDEPENDENTES EM AGENCIAS DIFERENTES (sem relacao causal)"
  fazer_login
  passo "Um deposito em cada agencia, disparados ao mesmo tempo, sem mensagem entre elas"
  curl -s -o /dev/null -X POST "$(url_da_agencia 0)/contas/3/depositar" \
    -H "Authorization: Bearer $ANA" -H 'Content-Type: application/json' -d '{"valor":5}' &
  curl -s -o /dev/null -X POST "$(url_da_agencia 1)/contas/1/depositar" \
    -H "Authorization: Bearer $BRUNO" -H 'Content-Type: application/json' -d '{"valor":5}' &
  curl -s -o /dev/null -X POST "$(url_da_agencia 2)/contas/2/depositar" \
    -H "Authorization: Bearer $CARLA" -H 'Content-Type: application/json' -d '{"valor":5}' &
  wait
  for id in 0 1 2; do log_da_agencia "$id" 1; done
}

parte_local() {
  titulo "TRANSFERENCIA LOCAL (conta 0 -> conta 3, ambas na agencia 0: nao passa pelo RabbitMQ)"
  fazer_login
  passo "Transferindo R$ 40,00"
  chamar POST "$(url_da_agencia 0)/transferencias" "$ANA" '{"idOrigem":0,"idDestino":3,"valor":40}'
  passo "Eventos gravados pela agencia 0 (dois eventos LOCAIS: so a posicao 0 do vetor anda)"
  tail -2 "$AGENCIA_DIR/data/eventos-agencia-0.jsonl" | jq -c '{tipo, timestampVetorial, detalhes}'
}

parte_entre_agencias() {
  titulo "PARTE C - TRANSFERENCIA ENTRE AGENCIAS VIA RABBITMQ (conta 0 na ag.0 -> conta 1 na ag.1)"
  fazer_login
  passo "Estado das duas agencias ANTES"
  status_resumido 0; status_resumido 1
  passo "Transferindo R\$ 30,00 da conta 0 para a conta 1 (a agencia 0 so PUBLICA a mensagem)"
  chamar POST "$(url_da_agencia 0)/transferencias" "$ANA" '{"idOrigem":0,"idDestino":1,"valor":30}'
  passo "Log da agencia 0 (origem): debito e envio - a mensagem leva o vetor do envio"
  log_da_agencia 0 2
  sleep 2
  passo "Log da agencia 1 (destino): consumo assincrono da fila-agencia-1"
  log_da_agencia 1 1
  passo "Saldo na agencia de destino (conta 1) depois do consumo"
  chamar GET "$(url_da_agencia 1)/contas/1" "$BRUNO"
  passo "Estado das duas agencias DEPOIS (regra 3: max posicao a posicao, +1 na propria)"
  status_resumido 0; status_resumido 1
}

parte_resiliencia() {
  titulo "PARTE C - RESILIENCIA: AGENCIA DE DESTINO FORA DO AR"
  fazer_login
  passo "A agencia 1 esta no ar e tem a conta 1"
  status_resumido 1
  chamar GET "$(url_da_agencia 1)/contas/1" "$BRUNO"
  passo "Derrubando a agencia 1 (equivale a fechar o terminal dela)"
  "$RAIZ/scripts/parar-agencias.sh" 1
  sleep 2
  curl -s -o /dev/null -w "  GET agencia 1 /status -> HTTP %{http_code}  (000 = fora do ar)\n" \
    "$(url_da_agencia 1)/status"
  passo "Transferindo R\$ 10,00 da conta 0 para a conta 1 COM A AGENCIA 1 FORA DO AR"
  chamar POST "$(url_da_agencia 0)/transferencias" "$ANA" '{"idOrigem":0,"idDestino":1,"valor":10}'
  passo "Ninguem consumiu: a mensagem esta retida na fila duravel da agencia 1 (visto pela agencia 0)"
  status_resumido 0
  passo "Subindo a agencia 1 de novo (processo novo: as contas em memoria se perderam)"
  "$RAIZ/scripts/subir-agencias.sh" >/dev/null
  sleep 3
  passo "Log da agencia 1 ao reconectar no RabbitMQ"
  log_da_agencia 1 3
  passo "Estado da agencia 1 depois de consumir a fila"
  status_resumido 1
  passo "Saldo da conta 0 na origem (o debito continua aplicado)"
  chamar GET "$(url_da_agencia 0)/contas/0" "$ANA"
  echo
  echo "  >>> A mensagem NAO se perdeu: ficou na fila e foi entregue quando a agencia 1 voltou."
  echo "  >>> Mas a conta 1 vivia so em memoria e sumiu no reinicio: o credito nao tinha onde ser aplicado."
  echo "  >>> Com a dead-letter queue (funcionalidade adicional), ele foi para fila-agencia-1.mortas."
}

parte_mensagens_mortas() {
  titulo "FUNCIONALIDADE ADICIONAL - DEAD-LETTER QUEUE (continuacao da resiliencia)"
  fazer_login
  passo "O credito que nao achou a conta 1 nao foi descartado: esta na fila de mortas da agencia 1"
  status_resumido 1
  passo "Recriando a conta 1 na agencia 1 (bruno, saldo inicial 0)"
  chamar POST "$(url_da_agencia 1)/contas" "$BRUNO" '{"id":1,"nomeAluno":"Bruno","saldoInicial":0}'
  passo "Reprocessando as mensagens mortas da agencia 1"
  chamar POST "$(url_da_agencia 1)/mensagens-mortas/reprocessar" "$BRUNO"
  sleep 2
  passo "Log da agencia 1: a mensagem volta para a fila e agora o credito e aplicado"
  log_da_agencia 1 3
  passo "Saldo da conta 1: os R$ 10,00 da transferencia feita com a agencia fora do ar chegaram"
  chamar GET "$(url_da_agencia 1)/contas/1" "$BRUNO"
  passo "Filas depois do reprocessamento"
  status_resumido 1
}

parte_linha_do_tempo() {
  titulo "PARTE D - LINHA DO TEMPO CAUSAL (relogio vetorial)"
  (cd "$AGENCIA_DIR" && ./mvnw -q exec:java@mesclar-logs 2>/dev/null)
}

parte_auth_sem_token() {
  titulo "JWT (Sprint 1) - CENARIO (a): requisicao SEM token"
  passo "GET /contas/0 sem o cabecalho Authorization"
  chamar GET "$(url_da_agencia 0)/contas/0"
  passo "POST /transferencias sem token"
  chamar POST "$(url_da_agencia 0)/transferencias" "" '{"idOrigem":0,"idDestino":1,"valor":10}'
  passo "A rota publica de health-check continua acessivel (nao mexe em conta)"
  chamar GET "$(url_da_agencia 0)/status"
}

parte_auth_com_token() {
  titulo "JWT (Sprint 1) - CENARIO (b): requisicao COM token valido"
  local token; token=$(obter_token ana ana123 0)
  passo "GET /contas/0 com o token"
  chamar GET "$(url_da_agencia 0)/contas/0" "$token"
  passo "Senha errada nao emite token"
  chamar POST "$(url_da_agencia 0)/auth/login" "" '{"usuario":"ana","senha":"errada"}'
}

parte_auth_token_expirado() {
  titulo "JWT (Sprint 1) - CENARIO (c): requisicao com token EXPIRADO"
  local expirado; expirado=$(cd "$AGENCIA_DIR" && ./mvnw -q exec:java@token-expirado 2>/dev/null | tail -1)
  passo "GET /contas/0 com o token expirado"
  chamar GET "$(url_da_agencia 0)/contas/0" "$expirado"
  passo "A rota interna do Sprint 1 (/creditar-remoto) nao existe mais: o credito vem pela fila"
  chamar POST "$(url_da_agencia 0)/contas/0/creditar-remoto" "$(obter_token ana ana123 0)" \
    '{"valor":10,"vetorEnvio":[0,1,0],"origemAgencia":1}'
}

parte_auth() {
  parte_auth_sem_token
  parte_auth_com_token
  parte_auth_token_expirado
}

echo "Data/hora desta execucao: $(date)"
echo "Maquina: $(hostname)   Usuario: $(whoami)"

case "${1:-tudo}" in
  preparar)       parte_preparar ;;
  concorrentes)   parte_concorrentes ;;
  local)          parte_local ;;
  entre-agencias) parte_entre_agencias ;;
  resiliencia)    parte_resiliencia ;;
  mortas)         parte_mensagens_mortas ;;
  linha-do-tempo) parte_linha_do_tempo ;;
  auth)                parte_auth ;;
  auth-sem-token)      parte_auth_sem_token ;;
  auth-com-token)      parte_auth_com_token ;;
  auth-token-expirado) parte_auth_token_expirado ;;
  tudo)
    parte_preparar; parte_concorrentes; parte_local; parte_entre_agencias
    parte_auth; parte_resiliencia; parte_mensagens_mortas; parte_linha_do_tempo ;;
  *) echo "Parte desconhecida: $1"; exit 1 ;;
esac
