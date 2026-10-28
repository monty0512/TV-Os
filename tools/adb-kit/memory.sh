#!/usr/bin/env bash
# TV-Os · Mide la memoria del Inicio en pantalla y en segundo plano. No modifica nada.
# Objetivo: < 70 MB PSS en pantalla; en segundo plano debe bajar (banners liberados).
# Abre YouTube (o Play Store) unos segundos y vuelve al Inicio.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/common.sh"
connect_tv "${1:-}"
LIMIT_KB=71680

pss_kb() {
  local out pss
  out="$(sh_ dumpsys meminfo "$TVOS_PKG")"
  pss="$(grep -E 'TOTAL PSS:' <<<"$out" | head -n1 | awk '{print $3}')"
  [ -z "$pss" ] && pss="$(grep -E '^ +TOTAL ' <<<"$out" | head -n1 | awk '{print $2}')"
  echo "$pss"
  grep -E 'Java Heap:|Native Heap:|Code:|Graphics:' <<<"$out" | head -n4 >&2
}

sh_ input keyevent KEYCODE_HOME >/dev/null
echo "1/2 · Inicio en pantalla (esperando 8 s)…"
sleep 8
FG="$(pss_kb)"
[ -z "$FG" ] && { echo "No se pudo leer la memoria. ¿Está TV-Os instalado como Inicio?"; exit 1; }

# Una app a pantalla completa (los Ajustes de Android TV son un panel y no ocultan el Inicio).
BG_PKG="com.google.android.youtube.tv"
sh_ pm list packages | grep -qx "package:$BG_PKG" || BG_PKG="com.android.vending"
echo "2/2 · TV-Os en segundo plano: se abre $BG_PKG (esperando 10 s)…"
sh_ monkey -p "$BG_PKG" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null
sleep 10
BG="$(pss_kb)"
sh_ input keyevent KEYCODE_HOME >/dev/null

echo
echo "En pantalla:       $((FG / 1024)) MB · límite $((LIMIT_KB / 1024)) MB"
[ -n "$BG" ] && echo "En segundo plano:  $((BG / 1024)) MB"
if [ "$FG" -le "$LIMIT_KB" ]; then echo "RESULTADO: dentro del presupuesto"; else echo "RESULTADO: por encima del presupuesto"; fi
