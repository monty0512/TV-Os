#!/usr/bin/env bash
# TV-Os · Deshace cleanup: vuelve cada app, restricción y animación al estado guardado en el respaldo.
# Uso: ./cleanup-restore.sh [IP-de-la-TV] [respaldo]
# Sin respaldo aplica todos los de reports/ del más nuevo al más antiguo: el más antiguo (estado original) gana.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/common.sh"

if [ -n "${2:-}" ]; then BACKUPS="$2"; else BACKUPS=$(ls -1 "$DIR"/reports/limpieza-respaldo-*.txt 2>/dev/null | sort -r); fi
if [ -z "$BACKUPS" ] || [ ! -f "$(echo "$BACKUPS" | head -n1)" ]; then
  echo "ERROR: no se encontró ningún respaldo en reports/. Indica la ruta: ./cleanup-restore.sh 192.168.1.50 ruta/respaldo.txt"
  exit 1
fi

connect_tv "${1:-}"

echo "Se va a restaurar en la TV el estado guardado en:"
echo "$BACKUPS" | sed 's/^/    /'
echo "  - Reactivar las apps que estaban activas antes de la limpieza"
echo "  - Quitar las restricciones de segundo plano"
echo "  - Volver las animaciones a su valor anterior"
read -r -p "¿Continuar? (s/N) " ok
case "$ok" in s|S|si|sí|Si|Sí) ;; *) echo "Cancelado. No se cambió nada."; exit 0 ;; esac

for BACKUP in $BACKUPS; do
while read -r kind a b; do
  case "$kind" in
    anim)
      if [ "$b" = null ]; then sh_ settings delete global "$a" >/dev/null; else sh_ settings put global "$a" "$b" >/dev/null; fi
      echo "OK: $a = $b" ;;
    pkg)
      [ "$b" = enabled ] || continue
      sh_ pm enable "$a" >/dev/null
      echo "OK: reactivada $a" ;;
    appops)
      sh_ cmd appops set "$a" RUN_ANY_IN_BACKGROUND "$b" >/dev/null
      echo "OK: $a segundo plano = $b" ;;
    bucket)
      case "$b" in ''|*[!0-9]*) continue ;; esac
      sh_ am set-standby-bucket "$a" "$b" >/dev/null ;;
  esac
done < "$BACKUP"
done
echo "Listo. Reinicia la TV."
