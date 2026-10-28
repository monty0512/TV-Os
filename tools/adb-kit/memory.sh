#!/usr/bin/env bash
# TV-Os · Mide la memoria del Inicio (objetivo Fase 1: < 60 MB PSS). Solo lectura.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/common.sh"
connect_tv "${1:-}"
LIMIT_KB=61440

sh_ input keyevent KEYCODE_HOME >/dev/null
echo "Esperando 8 s a que el Inicio se estabilice…"
sleep 8
OUT="$(sh_ dumpsys meminfo "$TVOS_PKG")"
PSS="$(grep -E 'TOTAL PSS:' <<<"$OUT" | head -n1 | awk '{print $3}')"
[ -z "$PSS" ] && PSS="$(grep -E '^ +TOTAL ' <<<"$OUT" | head -n1 | awk '{print $2}')"
if [ -z "$PSS" ]; then
  echo "No se pudo leer la memoria. ¿Está TV-Os abierto?"
  exit 1
fi
echo "TV-Os usa $((PSS / 1024)) MB (PSS) · límite $((LIMIT_KB / 1024)) MB"
if [ "$PSS" -le "$LIMIT_KB" ]; then echo "RESULTADO: dentro del presupuesto"; else echo "RESULTADO: por encima del presupuesto"; fi
echo
grep -E 'Java Heap|Native Heap|Code:|Graphics:|TOTAL' <<<"$OUT" | head -n 8
