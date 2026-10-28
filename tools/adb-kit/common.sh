# Funciones compartidas por los scripts del kit ADB (bash).
ADB="${ADB:-adb}"
TVOS_PKG="com.jhonatan.tvos"
TVOS_HOME="$TVOS_PKG/.MainActivity"
BACKUP_HOME="com.jhonatan.milauncher/.MainActivity"
TVOS_A11Y="$TVOS_PKG/$TVOS_PKG.system.TvOsAccessibilityService"

connect_tv() {
  local target="${1:-}"
  if ! command -v "$ADB" >/dev/null 2>&1; then
    echo "ERROR: no se encontró 'adb'. Instala Android Platform Tools:"
    echo "  https://developer.android.com/tools/releases/platform-tools"
    exit 1
  fi
  if [ -n "$target" ]; then
    case "$target" in *:*) ;; *) target="$target:5555" ;; esac
    "$ADB" connect "$target" >/dev/null
    ADB_DEV=("$ADB" -s "$target")
  else
    ADB_DEV=("$ADB")
  fi
  if [ "$("${ADB_DEV[@]}" get-state 2>/dev/null)" != "device" ]; then
    echo "ERROR: no hay ninguna TV conectada o falta aceptar el aviso de depuración en la TV."
    exit 1
  fi
}

sh_() { "${ADB_DEV[@]}" shell "$@" </dev/null 2>&1 | tr -d '\r'; }

current_home() {
  sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n1
}

# Paquetes que la limpieza nunca toca (sistema de la TV, Play Store, Chromecast, Asistente, teclado, TV-Os y respaldo).
is_protected() {
  case "$1" in
    com.mediatek.*|mediatek.*|com.mstar.*|mitv.service|com.xiaomi.mitv.res|com.android.tv.settings|\
    com.duokan.factorytest|com.android.vending|com.google.android.apps.mediashell|com.google.android.katniss|\
    com.google.android.tv.assistant|com.google.android.gms|com.google.android.inputmethod.latin|\
    "$TVOS_PKG"|com.jhonatan.milauncher|android|com.android.systemui) return 0 ;;
  esac
  return 1
}

# Paquetes de una lista (sin comentarios ni líneas vacías).
read_list() { sed -e 's/#.*//' -e 's/[[:space:]]*$//' -e 's/^[[:space:]]*//' "$1" | grep -v '^$'; }

# Estado de un paquete: enabled, disabled o missing.
pkg_state() {
  if [ -z "$(sh_ pm path "$1")" ]; then echo missing
  elif sh_ pm list packages -d | grep -qx "package:$1"; then echo disabled
  else echo enabled; fi
}

ANIM_KEYS="window_animation_scale transition_animation_scale animator_duration_scale"
