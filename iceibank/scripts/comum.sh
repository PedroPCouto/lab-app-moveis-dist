#!/usr/bin/env bash
set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AGENCIA_DIR="$RAIZ/agencia"
JAR="$AGENCIA_DIR/target/iceibank-0.0.1-SNAPSHOT.jar"
PID_DIR="$AGENCIA_DIR/data/.pids"

OFFSET="${OFFSET:-0}"
NUMERO_AGENCIAS=3

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) WINDOWS=sim ;;
  *)                    WINDOWS=nao ;;
esac

JAVA_MINIMO=25

versao_major_do_java() {
  "$1" -version 2>&1 | head -1 | grep -oP '"\K[0-9]+' | head -1
}

resolver_java() {
  local candidato
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    [ "$(versao_major_do_java "$JAVA_HOME/bin/java")" -ge "$JAVA_MINIMO" ] 2>/dev/null && return 0
  fi
  if command -v java >/dev/null; then
    [ "$(versao_major_do_java java)" -ge "$JAVA_MINIMO" ] 2>/dev/null && return 0
  fi
  for candidato in /usr/lib/jvm/*/bin/java; do
    [ -x "$candidato" ] || continue
    if [ "$(versao_major_do_java "$candidato")" -ge "$JAVA_MINIMO" ] 2>/dev/null; then
      export JAVA_HOME="$(dirname "$(dirname "$candidato")")"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  done
  echo "AVISO: nenhum JDK $JAVA_MINIMO+ encontrado. O projeto compila para Java $JAVA_MINIMO." >&2
  return 1
}

resolver_java

porta_da_agencia() { echo $((4000 + OFFSET + $1)); }
url_da_agencia()   { echo "http://localhost:$(porta_da_agencia "$1")"; }

titulo() {
  echo
  echo "=================================================================="
  echo " $*"
  echo "=================================================================="
}

passo() { echo; echo "--- $* ---"; }

status_resumido() {
  curl -sf "$(url_da_agencia "$1")/status" \
    | jq -c '{agencia: .idAgencia, vetor: .relogioVetorial, contas, filas}' 2>/dev/null \
    || echo "  agencia $1 fora do ar"
}

# Linhas "[Vetor ...]" que a agencia imprimiu no console (data/agencia-N.out), sem o
# prefixo do logger: e o "log das agencias" que as evidencias do Sprint 2 pedem.
log_da_agencia() {
  local id="$1" linhas="${2:-3}"
  grep -a '\[Vetor' "$AGENCIA_DIR/data/agencia-$id.out" 2>/dev/null | tail -"$linhas" \
    | sed -E 's/^([0-9:.]+) +INFO +RegistroEventos +: /\1 /' | sed "s/^/  [agencia-$id] /"
}

obter_token() {
  local usuario="$1" senha="$2" agencia="$3"
  curl -s -X POST "$(url_da_agencia "$agencia")/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"usuario\":\"$usuario\",\"senha\":\"$senha\"}" | jq -r '.token // empty'
}

chamar() {
  local metodo="$1" url="$2" token="${3:-}" corpo="${4:-}"
  local argumentos=(-s -w '\nHTTP %{http_code}\n' -X "$metodo" "$url")
  [ -n "$token" ] && argumentos+=(-H "Authorization: Bearer $token")
  if [ -n "$corpo" ]; then
    argumentos+=(-H 'Content-Type: application/json' -d "$corpo")
  fi
  curl "${argumentos[@]}"
}
