#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/comum.sh"

[ "${1:-}" = "--limpar-logs" ] && rm -f "$AGENCIA_DIR"/data/*.jsonl

mkdir -p "$PID_DIR"

LANCADOR="$(command -v setsid || command -v nohup)"

if [ ! -f "$JAR" ]; then
  echo "Jar nao encontrado, compilando..."
  (cd "$AGENCIA_DIR" && ./mvnw -q package -DskipTests) || exit 1
fi

for id in $(seq 0 $((NUMERO_AGENCIAS - 1))); do
  porta=$(porta_da_agencia "$id")
  if curl -sf "$(url_da_agencia "$id")/status" >/dev/null 2>&1; then
    echo "Agencia $id ja esta no ar na porta $porta."
    continue
  fi
  ( cd "$AGENCIA_DIR" \
    && AGENCIA_ID="$id" OFFSET="$OFFSET" $LANCADOR java -jar "$JAR" \
         < /dev/null > "$AGENCIA_DIR/data/agencia-$id.out" 2>&1 &
    echo $! > "$PID_DIR/agencia-$id.pid" )
  echo "Subindo agencia $id (porta $porta, pid $(cat "$PID_DIR/agencia-$id.pid"))..."
done

echo
echo -n "Aguardando as agencias responderem"
for _ in $(seq 1 60); do
  no_ar=0
  for id in $(seq 0 $((NUMERO_AGENCIAS - 1))); do
    curl -sf "$(url_da_agencia "$id")/status" >/dev/null 2>&1 && no_ar=$((no_ar + 1))
  done
  [ "$no_ar" -eq "$NUMERO_AGENCIAS" ] && break
  echo -n "."
  sleep 1
done
echo

for id in $(seq 0 $((NUMERO_AGENCIAS - 1))); do
  curl -s "$(url_da_agencia "$id")/status" \
    | jq -c '{agencia: .idAgencia, porta, lamport: .relogioLamport, contas: .quantidadeContas}' \
    || echo "Agencia $id NAO subiu - veja $AGENCIA_DIR/data/agencia-$id.out"
done
