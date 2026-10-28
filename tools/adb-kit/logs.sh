#!/usr/bin/env bash
# TV-Os · Captura qué teclas recibe TV-Os y qué apps se abren (y cuántas veces). Solo lectura.
# Uso: ./logs.sh [IP]  → reproduce el problema en la TV y pulsa Enter.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/common.sh"
connect_tv "${1:-}"
mkdir -p "$DIR/reports"
OUT="$DIR/reports/logs-$(date +%Y%m%d-%H%M%S).txt"

"${ADB_DEV[@]}" logcat -c
echo "Registro en marcha. En la TV:"
echo "  1. Pulsa el botón ≡ y otros botones del mando estando en el Inicio de TV-Os."
echo "  2. Abre Netflix desde TV-Os."
read -r -p "Cuando termines, pulsa Enter aquí… " _
"${ADB_DEV[@]}" logcat -d -v time | tr -d '\r' \
  | grep -E "TvOs|START u0|Start proc|Displayed|ActivityTaskManager: (START|Force)|am_proc_died|Killing" >"$OUT"
echo
echo "== Teclas que recibió TV-Os =="
grep -E "TvOs.*key " "$OUT" | sed 's/.*key /  /' | sort | uniq -c
echo "== Apps abiertas por TV-Os =="
grep -E "TvOs.*launch " "$OUT" | sed 's/.*launch /  /'
echo "== Arranques de actividades (sistema) =="
grep -E "START u0" "$OUT" | sed -E 's/.*cmp=([^ }]+).*/  \1/' | head -n 20
echo
echo "Informe completo: $OUT"
