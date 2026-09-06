#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/comum.sh"

PORTA="${1:-5500}"
echo "Frontend em http://localhost:$PORTA  (Ctrl+C para parar)"
cd "$RAIZ/frontend" && python3 -m http.server "$PORTA"
