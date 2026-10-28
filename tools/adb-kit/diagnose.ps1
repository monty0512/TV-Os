# TV-Os · Fase 0: diagnóstico de la TV (Windows / PowerShell).
# SOLO LECTURA: no instala, no desactiva ni modifica nada en el dispositivo.
#
# Uso:
#   .\diagnose.ps1                   # usa el único dispositivo conectado
#   .\diagnose.ps1 192.168.1.50      # conecta por Wi-Fi (puerto 5555) y diagnostica
#
# Genera un informe en .\reports\diagnostico-<fecha>.txt

param([string]$Target = "")

$ErrorActionPreference = "Continue"
$Adb = if ($env:ADB) { $env:ADB } else { "adb" }

if (-not (Get-Command $Adb -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: no se encontró 'adb'. Instala Android Platform Tools:"
    Write-Host "  https://developer.android.com/tools/releases/platform-tools"
    exit 1
}

$AdbArgs = @()
if ($Target -ne "") {
    if ($Target -notmatch ":") { $Target = "${Target}:5555" }
    & $Adb connect $Target | Out-Null
    $AdbArgs = @("-s", $Target)
}

$state = (& $Adb @AdbArgs get-state 2>$null) -join ""
if ($state.Trim() -ne "device") {
    Write-Host "ERROR: no hay ninguna TV conectada o falta autorizar la depuración."
    Write-Host "  1. En la TV: Ajustes > Preferencias del dispositivo > Información > pulsa 7 veces 'Compilación'."
    Write-Host "  2. Activa 'Depuración USB' (y 'Depuración de red' si conectas por Wi-Fi)."
    Write-Host "  3. Acepta el aviso de autorización que aparece en la TV."
    exit 1
}

$OutDir = Join-Path $PSScriptRoot "reports"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Report = Join-Path $OutDir ("diagnostico-{0}.txt" -f (Get-Date -Format "yyyyMMdd-HHmmss"))

function Sh([string]$Cmd) {
    $out = & $Adb @AdbArgs shell $Cmd 2>&1 | ForEach-Object { "$_".TrimEnd("`r") }
    return @($out)
}
function One([string]$Cmd) { return ((Sh $Cmd) -join " ").Trim() }
function Pkgs([string]$Flags) {
    return @(Sh "pm list packages $Flags" | Where-Object { $_ -like "package:*" } | ForEach-Object { $_.Substring(8) })
}

$All = Pkgs ""
$Sys = Pkgs "-s"
$User = Pkgs "-3"
$Disabled = Pkgs "-d"

$r = New-Object System.Collections.Generic.List[string]
function Add([string]$s) { $r.Add($s) }
function Section([string]$t) { Add ""; Add "== $t ==" }
function Check([string]$label, [string]$pkg) {
    if ($All -contains $pkg) { Add "  [OK]  $label" } else { Add "  [NO]  $label" }
}

Add "TV-Os · Informe de diagnóstico (Fase 0)"
Add ("Fecha: " + (Get-Date -Format "yyyy-MM-dd HH:mm:ss"))
Add "Modo: solo lectura"

Section "Dispositivo"
Add ("  Marca:            " + (One "getprop ro.product.brand"))
Add ("  Modelo:           " + (One "getprop ro.product.model"))
Add ("  Nombre interno:   " + (One "getprop ro.product.device"))
Add ("  Android:          " + (One "getprop ro.build.version.release") + " (API " + (One "getprop ro.build.version.sdk") + ")")
Add ("  Parche seguridad: " + (One "getprop ro.build.version.security_patch"))
Add ("  Compilación:      " + (One "getprop ro.build.display.id"))
Add ("  Plataforma (SoC): " + (One "getprop ro.board.platform") + " / " + (One "getprop ro.hardware"))
Add ("  ABI:              " + (One "getprop ro.product.cpu.abilist"))
Add ("  Núcleos CPU:      " + (One "grep -c ^processor /proc/cpuinfo"))
Add ("  Pantalla:         " + (One "wm size") + " · " + (One "wm density"))
Add ("  Leanback (TV):    " + (One "pm list features | grep -c android.software.leanback") + " característica(s)")

Section "Memoria"
Sh "grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo" | ForEach-Object { Add "  $_" }
Add ("  Low-RAM device:   " + (One "getprop ro.config.low_ram"))

Section "Almacenamiento (/data)"
Sh "df -h /data" | ForEach-Object { Add "  $_" }

Section "Launcher actual"
Add ("  Actividad HOME:   " + ((Sh "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME") | Select-Object -Last 1))
Add "  Candidatos HOME:"
Sh "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME" |
    Where-Object { $_ -match "/" } | ForEach-Object { Add "    $($_.Trim())" }

Section "Componentes que TV-Os necesita"
Check "Proveedor de TV (com.android.providers.tv)" "com.android.providers.tv"
Check "Google Backdrop (com.google.android.backdrop)" "com.google.android.backdrop"
Check "Google Play Store (com.android.vending)" "com.android.vending"
Check "Asistente / Katniss (com.google.android.katniss)" "com.google.android.katniss"
Check "Launcher de Google (com.google.android.tvlauncher)" "com.google.android.tvlauncher"
Check "Google TV (com.google.android.apps.tv.launcherx)" "com.google.android.apps.tv.launcherx"

Section "Permiso READ_TV_LISTINGS (Top Shelf / Continuar viendo)"
$perm = Sh "dumpsys package permission android.permission.READ_TV_LISTINGS"
if (($perm -join "`n") -match "READ_TV_LISTINGS") {
    Add "  Definido en el sistema: sí"
    $perm | Where-Object { $_ -match "prot=|protectionLevel" } | Select-Object -First 3 | ForEach-Object { Add "  $($_.Trim())" }
} else {
    Add "  Definido en el sistema: no encontrado"
}
Add "  Prueba de lectura desde el shell (puede ser denegada; no es concluyente):"
foreach ($uri in @("watch_next_program", "preview_program", "channel")) {
    $res = Sh "content query --uri content://android.media.tv/$uri --projection _id"
    if (($res -join "`n") -match "(?i)exception|denial|not allowed|security") {
        Add "    ${uri}: acceso denegado al shell"
    } else {
        Add ("    ${uri}: " + @($res | Where-Object { $_ -like "Row:*" }).Count + " fila(s) visibles")
    }
}

Section "Salvapantallas"
Add ("  Activado:         " + (One "settings get secure screensaver_enabled"))
Add ("  Componente:       " + (One "settings get secure screensaver_components"))
Add ("  Tiempo (ms):      " + (One "settings get system screen_off_timeout"))

Section "Animaciones del sistema"
Add ("  window_animation_scale:     " + (One "settings get global window_animation_scale"))
Add ("  transition_animation_scale: " + (One "settings get global transition_animation_scale"))
Add ("  animator_duration_scale:    " + (One "settings get global animator_duration_scale"))

Section "Paquetes de Xiaomi / PatchWall / fabricante (candidatos a limpieza)"
$vendor = @($All | Where-Object { $_ -match "(?i)xiaomi|mitv|miui|patchwall|\.mi\.|duokan|mstar|mediatek|cvte|com\.android\.tv\." } | Sort-Object)
if ($vendor.Count -gt 0) { $vendor | ForEach-Object { Add "  $_" } } else { Add "  (ninguno)" }

Section "Resumen de paquetes"
Add ("  Total:        " + $All.Count)
Add ("  Del sistema:  " + $Sys.Count)
Add ("  Instalados:   " + $User.Count)
Add ("  Desactivados: " + $Disabled.Count)

Section "Apps instaladas por el usuario"
$User | Sort-Object | ForEach-Object { Add "  $_" }

Section "Paquetes desactivados"
if ($Disabled.Count -gt 0) { $Disabled | Sort-Object | ForEach-Object { Add "  $_" } } else { Add "  (ninguno)" }

Section "Procesos que más memoria usan (top 15)"
$mem = Sh "dumpsys meminfo"
$start = [Array]::FindIndex([string[]]$mem, [Predicate[string]]{ param($l) $l -match "Total PSS by process:" })
if ($start -ge 0) {
    $mem[$start..([Math]::Min($start + 15, $mem.Count - 1))] | ForEach-Object { Add "  $_" }
}

Section "Lista completa de paquetes"
$All | Sort-Object | ForEach-Object { Add "  $_" }

$r | Set-Content -Path $Report -Encoding UTF8
Write-Host "Diagnóstico completado. Informe: $Report"
Write-Host "No se modificó nada en la TV."
