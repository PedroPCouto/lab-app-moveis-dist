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
  echo "$pid"
}

alvos=("$@")
[ ${#alvos[@]} -eq 0 ] && alvos=($(seq 0 $((NUMERO_AGENCIAS - 1))))

for id in "${alvos[@]}"; do
  porta=$(porta_da_agencia "$id")
  pid=$(pid_na_porta "$porta")

  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null && echo "Agencia $id derrubada (porta $porta, pid $pid)."
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
