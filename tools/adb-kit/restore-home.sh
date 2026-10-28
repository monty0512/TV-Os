#!/usr/bin/env bash
# TV-Os · Vuelve a poner MiLauncher como Inicio (no desinstala TV-Os).
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/common.sh"
connect_tv "${1:-}"
sh_ cmd package set-home-activity "$BACKUP_HOME"
sh_ input keyevent KEYCODE_HOME >/dev/null
echo "Inicio actual: $(current_home)"
