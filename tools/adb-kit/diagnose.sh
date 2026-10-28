#!/usr/bin/env bash
# TV-Os · Fase 0: diagnóstico de la TV.
# SOLO LECTURA: no instala, no desactiva ni modifica nada en el dispositivo.
#
# Uso:
#   ./diagnose.sh                 # usa el único dispositivo conectado
#   ./diagnose.sh 192.168.1.50    # conecta por Wi-Fi (puerto 5555) y diagnostica
#
# Genera un informe en ./reports/diagnostico-<fecha>.txt

set -u

ADB="${ADB:-adb}"
TARGET="${1:-}"
OUT_DIR="$(cd "$(dirname "$0")" && pwd)/reports"
STAMP="$(date +%Y%m%d-%H%M%S)"
REPORT="$OUT_DIR/diagnostico-$STAMP.txt"

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "ERROR: no se encontró 'adb'. Instala Android Platform Tools:"
  echo "  https://developer.android.com/tools/releases/platform-tools"
  exit 1
fi

if [ -n "$TARGET" ]; then
  case "$TARGET" in
    *:*) ;;
    *) TARGET="$TARGET:5555" ;;
  esac
  "$ADB" connect "$TARGET" >/dev/null
  ADB_DEV=("$ADB" -s "$TARGET")
else
  ADB_DEV=("$ADB")
fi

if [ "$("${ADB_DEV[@]}" get-state 2>/dev/null)" != "device" ]; then
  echo "ERROR: no hay ninguna TV conectada o falta autorizar la depuración."
  echo "  1. En la TV: Ajustes > Preferencias del dispositivo > Información > pulsa 7 veces 'Compilación'."
  echo "  2. Activa 'Depuración USB' (y 'Depuración de red' si conectas por Wi-Fi)."
  echo "  3. Acepta el aviso de autorización que aparece en la TV."
  exit 1
fi

mkdir -p "$OUT_DIR"

sh_() { "${ADB_DEV[@]}" shell "$@" 2>&1 | tr -d '\r'; }

section() { printf '\n== %s ==\n' "$1"; }

check() {
  # check <etiqueta> <0|1>
  if [ "$2" = "1" ]; then printf '  [OK]  %s\n' "$1"; else printf '  [NO]  %s\n' "$1"; fi
}

has_pkg() { grep -qx "package:$1" <<<"$ALL_PKGS" && echo 1 || echo 0; }

ALL_PKGS="$(sh_ pm list packages)"
SYS_PKGS="$(sh_ pm list packages -s)"
USER_PKGS="$(sh_ pm list packages -3)"
DISABLED_PKGS="$(sh_ pm list packages -d)"

{
  echo "TV-Os · Informe de diagnóstico (Fase 0)"
  echo "Fecha: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "Modo: solo lectura"

  section "Dispositivo"
  echo "  Marca:            $(sh_ getprop ro.product.brand)"
  echo "  Modelo:           $(sh_ getprop ro.product.model)"
  echo "  Nombre interno:   $(sh_ getprop ro.product.device)"
  echo "  Android:          $(sh_ getprop ro.build.version.release) (API $(sh_ getprop ro.build.version.sdk))"
  echo "  Parche seguridad: $(sh_ getprop ro.build.version.security_patch)"
  echo "  Compilación:      $(sh_ getprop ro.build.display.id)"
  echo "  Plataforma (SoC): $(sh_ getprop ro.board.platform) / $(sh_ getprop ro.hardware)"
  echo "  ABI:              $(sh_ getprop ro.product.cpu.abilist)"
  echo "  Núcleos CPU:      $(sh_ 'grep -c ^processor /proc/cpuinfo')"
  echo "  Pantalla:         $(sh_ wm size | tail -n1) · $(sh_ wm density | tail -n1)"
  echo "  Leanback (TV):    $(sh_ pm list features | grep -c 'android.software.leanback') característica(s)"

  section "Memoria"
  sh_ 'grep -E "^(MemTotal|MemAvailable|SwapTotal|SwapFree):" /proc/meminfo' | sed 's/^/  /'
  echo "  Low-RAM device:   $(sh_ getprop ro.config.low_ram)"

  section "Almacenamiento (/data)"
  sh_ df -h /data | sed 's/^/  /'

  section "Launcher actual"
  echo "  Actividad HOME:   $(sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n1)"
  echo "  Candidatos HOME:"
  sh_ cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep '/' | sed 's/^/    /'

  section "Componentes que TV-Os necesita"
  check "Proveedor de TV (com.android.providers.tv)" "$(has_pkg com.android.providers.tv)"
  check "Google Backdrop (com.google.android.backdrop)" "$(has_pkg com.google.android.backdrop)"
  check "Google Play Store (com.android.vending)" "$(has_pkg com.android.vending)"
  check "Asistente / Katniss (com.google.android.katniss)" "$(has_pkg com.google.android.katniss)"
  check "Launcher de Google (com.google.android.tvlauncher)" "$(has_pkg com.google.android.tvlauncher)"
  check "Google TV (com.google.android.apps.tv.launcherx)" "$(has_pkg com.google.android.apps.tv.launcherx)"

  section "Permiso READ_TV_LISTINGS (Top Shelf / Continuar viendo)"
  PERM_INFO="$(sh_ dumpsys package permission android.permission.READ_TV_LISTINGS)"
  if grep -q 'READ_TV_LISTINGS' <<<"$PERM_INFO"; then
    echo "  Definido en el sistema: sí"
    grep -iE 'prot=|protectionLevel' <<<"$PERM_INFO" | head -n3 | sed 's/^ */  /'
  else
    echo "  Definido en el sistema: no encontrado"
  fi
  echo "  Prueba de lectura desde el shell (puede ser denegada; no es concluyente):"
  for uri in watch_next_program preview_program channel; do
    RES="$(sh_ content query --uri "content://android.media.tv/$uri" --projection _id | head -n 200)"
    if grep -qiE 'exception|denial|not allowed|security' <<<"$RES"; then
      echo "    $uri: acceso denegado al shell"
    else
      echo "    $uri: $(grep -c '^Row:' <<<"$RES") fila(s) visibles"
    fi
  done

  section "Salvapantallas"
  echo "  Activado:         $(sh_ settings get secure screensaver_enabled)"
  echo "  Componente:       $(sh_ settings get secure screensaver_components)"
  echo "  Tiempo (ms):      $(sh_ settings get system screen_off_timeout)"

  section "Animaciones del sistema"
  echo "  window_animation_scale:     $(sh_ settings get global window_animation_scale)"
  echo "  transition_animation_scale: $(sh_ settings get global transition_animation_scale)"
  echo "  animator_duration_scale:    $(sh_ settings get global animator_duration_scale)"

  section "Paquetes de Xiaomi / PatchWall / fabricante (candidatos a limpieza)"
  VENDOR_PKGS="$(grep -iE 'xiaomi|mitv|miui|patchwall|\.mi\.|duokan|mstar|mediatek|cvte|com\.android\.tv\.' <<<"$ALL_PKGS")"
  if [ -n "$VENDOR_PKGS" ]; then sed 's/^package:/  /' <<<"$VENDOR_PKGS" | sort; else echo "  (ninguno)"; fi

  section "Resumen de paquetes"
  echo "  Total:        $(grep -c '^package:' <<<"$ALL_PKGS")"
  echo "  Del sistema:  $(grep -c '^package:' <<<"$SYS_PKGS")"
  echo "  Instalados:   $(grep -c '^package:' <<<"$USER_PKGS")"
  echo "  Desactivados: $(grep -c '^package:' <<<"$DISABLED_PKGS")"

  section "Apps instaladas por el usuario"
  sed 's/^package:/  /' <<<"$USER_PKGS" | sort

  section "Paquetes desactivados"
  if [ -n "$DISABLED_PKGS" ]; then sed 's/^package:/  /' <<<"$DISABLED_PKGS" | sort; else echo "  (ninguno)"; fi

  section "Procesos que más memoria usan (top 15)"
  sh_ dumpsys meminfo | sed -n '/Total PSS by process:/,/^$/p' | head -n 16 | sed 's/^/  /'

  section "Lista completa de paquetes"
  sed 's/^package:/  /' <<<"$ALL_PKGS" | sort
} >"$REPORT"

echo "Diagnóstico completado. Informe: $REPORT"
echo "No se modificó nada en la TV."
