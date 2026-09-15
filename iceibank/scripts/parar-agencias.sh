#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/comum.sh"

pid_na_porta() {
  local porta="$1" pid=""
  if command -v ss >/dev/null; then
    pid=$(ss -ltnp 2>/dev/null | grep -F ":$porta " | grep -oP 'pid=\K[0-9]+' | head -1)
  fi
  if [ -z "$pid" ] && command -v lsof >/dev/null; then
    pid=$(lsof -ti "tcp:$porta" -sTCP:LISTEN 2>/dev/null | head -1)
  fi
  if [ -z "$pid" ] && [ "$WINDOWS" = sim ]; then
    pid=$(netstat -ano 2>/dev/null | grep -i LISTENING | grep -E "[:.]$porta[[:space:]]" \
            | awk '{print $NF}' | head -1)
  fi
  echo "$pid"
}

derrubar() {
  if [ "$WINDOWS" = sim ]; then
    taskkill //PID "$1" //F >/dev/null 2>&1
  else
    kill "$1" 2>/dev/null
  fi
}

alvos=("$@")
[ ${#alvos[@]} -eq 0 ] && alvos=($(seq 0 $((NUMERO_AGENCIAS - 1))))

for id in "${alvos[@]}"; do
  porta=$(porta_da_agencia "$id")
  pid=$(pid_na_porta "$porta")

  if [ -n "$pid" ]; then
    derrubar "$pid" && echo "Agencia $id derrubada (porta $porta, pid $pid)."
  else
    echo "Agencia $id nao esta escutando na porta $porta."
  fi
  rm -f "$PID_DIR/agencia-$id.pid"
done

for _ in $(seq 1 20); do
  ocupadas=0
  for id in "${alvos[@]}"; do
    [ -n "$(pid_na_porta "$(porta_da_agencia "$id")")" ] && ocupadas=$((ocupadas + 1))
  done
  [ "$ocupadas" -eq 0 ] && break
  sleep 0.5
done
