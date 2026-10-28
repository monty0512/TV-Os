#!/usr/bin/env bash
# TV-Os · Mide cuánto tarda en abrir una app desde cero (arranque en frío). Solo lectura salvo
# que cierra la app medida antes de abrirla (force-stop), igual que si la TV se acabara de encender.
# Uso: ./launch-time.sh [IP] [paquete ...]   (por defecto: Netflix y Disney+)
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/common.sh"
connect_tv "${1:-}"
shift || true
PKGS=("$@")
[ ${#PKGS[@]} -eq 0 ] && PKGS=(com.netflix.ninja com.disney.disneyplus)

echo "Inicio actual: $(current_home)"
for pkg in "${PKGS[@]}"; do
  COMP="$(sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER "$pkg" | tail -n1)"
  case "$COMP" in */*) ;; *) echo "$pkg: no instalada"; continue ;; esac
  sh_ input keyevent KEYCODE_HOME >/dev/null
  sleep 3
  sh_ am force-stop "$pkg" >/dev/null
  sleep 2
  OUT="$(sh_ am start -W -n "$COMP")"
  TOTAL="$(grep -E '^TotalTime:' <<<"$OUT" | awk '{print $2}')"
  WAIT="$(grep -E '^WaitTime:' <<<"$OUT" | awk '{print $2}')"
  echo "$pkg: TotalTime ${TOTAL:-?} ms · WaitTime ${WAIT:-?} ms"
  sleep 6
done
sh_ input keyevent KEYCODE_HOME >/dev/null
