#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/comum.sh"

PORTA="${1:-5500}"
echo "Frontend em http://localhost:$PORTA  (Ctrl+C para parar)"
PYTHON="$(command -v python3 || command -v python)"
cd "$RAIZ/frontend" && "$PYTHON" -m http.server "$PORTA"
