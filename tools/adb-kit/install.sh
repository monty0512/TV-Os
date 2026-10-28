#!/usr/bin/env bash
# TV-Os · Instala el APK y lo deja como Inicio. MiLauncher queda instalado como respaldo.
# Uso: ./install.sh [IP-de-la-TV] [ruta-al-apk]
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/common.sh"

APK="${2:-}"
if [ -z "$APK" ]; then
  for c in "$DIR/../../app/build/outputs/apk/release/app-release.apk" \
           "$DIR/../../app/build/outputs/apk/debug/app-debug.apk"; do
    [ -f "$c" ] && { APK="$c"; break; }
  done
fi
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "ERROR: no se encontró el APK. Compílalo en Android Studio (Build > Build APK(s))"
  echo "       o indica la ruta: ./install.sh 192.168.1.50 ruta/al/app.apk"
  exit 1
fi

connect_tv "${1:-}"

echo "Se va a hacer en la TV:"
echo "  1. Instalar/actualizar TV-Os: $APK"
echo "     (APK compilado el $(date -r "$APK" "+%Y-%m-%d %H:%M"))"
echo "  2. Poner TV-Os como Inicio (antes: $(current_home))"
echo "  3. Mantener MiLauncher instalado como respaldo"
echo "  4. Activar el servicio de accesibilidad de TV-Os (apagar pantalla; mantener ≡ o SET abre el Centro de control)"
read -r -p "¿Continuar? (s/N) " ok
case "$ok" in s|S|si|SI|sí|Sí) ;; *) echo "Cancelado. No se cambió nada."; exit 0 ;; esac

echo "Instalando…"
"${ADB_DEV[@]}" install -r "$APK" || { echo "ERROR: falló la instalación."; exit 1; }

echo "Versión instalada: $(sh_ dumpsys package "$TVOS_PKG" | grep -m1 versionName= | sed 's/.*versionName=//')"
echo "Poniendo TV-Os como Inicio…"
sh_ cmd package set-home-activity "$TVOS_HOME"
NOW="$(current_home)"
if [ "$NOW" = "$TVOS_HOME" ] || [ "$NOW" = "$TVOS_PKG/$TVOS_PKG.MainActivity" ]; then
  echo "OK: Inicio = $NOW"
else
  echo "AVISO: el Inicio sigue siendo '$NOW'. Pulsa Inicio en el mando y elige TV-Os con 'Siempre'."
fi
echo "Activando el servicio de accesibilidad de TV-Os…"
CUR="$(sh_ settings get secure enabled_accessibility_services)"
case "$CUR" in
  *"$TVOS_A11Y"*) NEW="$CUR" ;;
  ""|null) NEW="$TVOS_A11Y" ;;
  *) NEW="$CUR:$TVOS_A11Y" ;;   # conserva otros servicios ya activos
esac
sh_ settings put secure enabled_accessibility_services "$NEW" >/dev/null
sh_ settings put secure accessibility_enabled 1 >/dev/null
sleep 2
if sh_ dumpsys accessibility | grep -q "TvOsAccessibilityService"; then
  echo "OK: accesibilidad activada"
else
  echo "AVISO: el sistema no cargó el servicio de accesibilidad."
fi
sh_ input keyevent KEYCODE_HOME >/dev/null
echo
echo "Listo. Para volver a MiLauncher: ./restore-home.sh ${1:-}"
