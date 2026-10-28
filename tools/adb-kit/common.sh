# Funciones compartidas por los scripts del kit ADB (bash).
ADB="${ADB:-adb}"
TVOS_PKG="com.jhonatan.tvos"
TVOS_HOME="$TVOS_PKG/.MainActivity"
BACKUP_HOME="com.jhonatan.milauncher/.MainActivity"

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

sh_() { "${ADB_DEV[@]}" shell "$@" 2>&1 | tr -d '\r'; }

current_home() {
  sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n1
}
