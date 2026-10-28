#!/usr/bin/env bash
# TV-Os · Limpieza (Fase 4): desactiva las apps de debloat-list.txt, restringe en segundo plano las de
# autostart-list.txt y pone las animaciones a 0.5. Guarda antes el estado en reports/ para deshacerlo con cleanup-restore.
# Uso: ./cleanup.sh [IP-de-la-TV]
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/common.sh"

connect_tv "${1:-}"

DEBLOAT=$(read_list "$DIR/debloat-list.txt")
AUTOSTART=$(read_list "$DIR/autostart-list.txt")
for p in $DEBLOAT $AUTOSTART; do
  if is_protected "$p"; then echo "ERROR: '$p' está en la lista de paquetes que nunca se tocan. Quítalo de la lista."; exit 1; fi
done

echo "Leyendo el estado actual…"
mkdir -p "$DIR/reports"
BACKUP="$DIR/reports/limpieza-respaldo-$(date +%Y%m%d-%H%M%S).txt"
{
  for k in $ANIM_KEYS; do echo "anim $k $(sh_ settings get global "$k")"; done
  for p in $DEBLOAT; do echo "pkg $p $(pkg_state "$p")"; done
  for p in $AUTOSTART; do
    mode=$(sh_ cmd appops get "$p" RUN_ANY_IN_BACKGROUND | sed -n 's/.*RUN_ANY_IN_BACKGROUND: \([a-z]*\).*/\1/p' | head -n1)
    echo "appops $p ${mode:-default}"
    echo "bucket $p $(sh_ am get-standby-bucket "$p" | tail -n1)"
  done
} > "$BACKUP"

echo
echo "Se va a hacer en la TV:"
echo "  1. Desactivar estas apps (se pueden reactivar con cleanup-restore):"
grep '^pkg ' "$BACKUP" | while read -r _ p st; do
  case "$st" in
    enabled) echo "       $p" ;;
    disabled) echo "       $p (ya estaba desactivada)" ;;
    missing) echo "       $p (no está instalada, se omite)" ;;
  esac
done
echo "  2. Restringir en segundo plano (siguen funcionando al abrirlas):"
for p in $AUTOSTART; do echo "       $p"; done
echo "  3. Animaciones a 0.5 (ahora: $(grep '^anim window' "$BACKUP" | cut -d' ' -f3))"
echo "  Estado anterior guardado en: $BACKUP"
read -r -p "¿Continuar? (s/N) " ok
case "$ok" in s|S|si|sí|Si|Sí) ;; *) echo "Cancelado. No se cambió nada."; exit 0 ;; esac

grep '^pkg ' "$BACKUP" | while read -r _ p st; do
  [ "$st" = enabled ] || continue
  if sh_ pm disable-user --user 0 "$p" | grep -q "disabled-user"; then echo "OK: desactivada $p"
  else echo "AVISO: no se pudo desactivar $p"; fi
done
for p in $AUTOSTART; do
  [ "$(pkg_state "$p")" = missing ] && { echo "Omitida (no instalada): $p"; continue; }
  sh_ cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore >/dev/null
  sh_ am set-standby-bucket "$p" rare >/dev/null
  if sh_ cmd appops get "$p" RUN_ANY_IN_BACKGROUND | grep -q ignore; then echo "OK: restringida $p"
  else echo "AVISO: no se pudo restringir $p"; fi
done
for k in $ANIM_KEYS; do sh_ settings put global "$k" 0.5 >/dev/null; done
echo "OK: animaciones = $(sh_ settings get global window_animation_scale)"
echo
echo "Listo. Reinicia la TV para notar el arranque más limpio."
echo "Para deshacerlo: ./cleanup-restore.sh ${1:-<IP-de-la-TV>}"
