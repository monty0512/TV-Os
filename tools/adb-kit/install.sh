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
echo "  5. Permitir leer el contenido que publican las apps (Top Shelf y Continuar viendo)"
read -r -p "¿Continuar? (s/N) " ok
case "$ok" in s|S|si|SI|sí|Sí) ;; *) echo "Cancelado. No se cambió nada."; exit 0 ;; esac

echo "Instalando…"
OUT="$("${ADB_DEV[@]}" install -r "$APK" 2>&1)"; echo "$OUT"
if echo "$OUT" | grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE; then
  echo
  echo "El APK está firmado con otra clave que la versión instalada (cambio a la clave propia de la v1.0)."
  echo "Para continuar hay que desinstalar TV-Os: se borran los perfiles, fondos y fotos (se vuelven a crear en la bienvenida)."
  read -r -p "¿Desinstalar y volver a instalar? (s/N) " ok
  case "$ok" in s|S|si|SI|sí|Sí) ;; *) echo "Cancelado. TV-Os sigue como estaba."; exit 0 ;; esac
  sh_ cmd package set-home-activity "$BACKUP_HOME" >/dev/null   # que el Inicio no quede en la app de fábrica
  "${ADB_DEV[@]}" uninstall "$TVOS_PKG" >/dev/null
  OUT="$("${ADB_DEV[@]}" install "$APK" 2>&1)"; echo "$OUT"
fi
echo "$OUT" | grep -q Success || { echo "ERROR: falló la instalación."; exit 1; }

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
echo "Permitiendo leer el contenido de las apps…"
sh_ pm grant "$TVOS_PKG" android.permission.READ_TV_LISTINGS >/dev/null
if sh_ dumpsys package "$TVOS_PKG" | grep -q "READ_TV_LISTINGS: granted=true"; then
  echo "OK: permiso de contenido concedido"
else
  echo "AVISO: no se pudo conceder READ_TV_LISTINGS; el Top Shelf mostrará solo colores"
fi
sh_ input keyevent KEYCODE_HOME >/dev/null
echo
echo "Listo. Para volver a MiLauncher: ./restore-home.sh ${1:-}"
echo
read -r -p "¿Aplicar ahora la limpieza (apps desactivadas, arranque automático y animaciones)? (s/N) " ok
case "$ok" in s|S|si|SI|sí|Sí) "$DIR/cleanup.sh" "${1:-}" ;; esac
