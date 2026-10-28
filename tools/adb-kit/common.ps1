# Funciones compartidas por los scripts del kit ADB (PowerShell).
$script:Adb = if ($env:ADB) { $env:ADB } else { "adb" }
$script:TvosPkg = "com.jhonatan.tvos"
$script:TvosHome = "com.jhonatan.tvos/.MainActivity"
$script:BackupHome = "com.jhonatan.milauncher/.MainActivity"
$script:TvosA11y = "com.jhonatan.tvos/com.jhonatan.tvos.system.TvOsAccessibilityService"
$script:AdbArgs = @()

function Connect-Tv([string]$Target) {
    if (-not (Get-Command $script:Adb -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: no se encontró 'adb'. Instala Android Platform Tools:"
        Write-Host "  https://developer.android.com/tools/releases/platform-tools"
        exit 1
    }
    if ($Target -ne "") {
        if ($Target -notmatch ":") { $Target = "${Target}:5555" }
        & $script:Adb connect $Target | Out-Null
        $script:AdbArgs = @("-s", $Target)
    }
    $state = (& $script:Adb @script:AdbArgs get-state 2>$null) -join ""
    if ($state.Trim() -ne "device") {
        Write-Host "ERROR: no hay ninguna TV conectada o falta aceptar el aviso de depuración en la TV."
        exit 1
    }
}

function Sh([string]$Cmd) {
    return @(& $script:Adb @script:AdbArgs shell $Cmd 2>&1 | ForEach-Object { "$_".TrimEnd("`r") })
}

function Get-CurrentHome {
    return ((Sh "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME") | Select-Object -Last 1).Trim()
}

# Paquetes que la limpieza nunca toca (sistema de la TV, Play Store, Chromecast, Asistente, teclado, TV-Os y respaldo).
function Test-Protected([string]$Pkg) {
    if ($Pkg -match '^(com\.mediatek\.|mediatek\.|com\.mstar\.)') { return $true }
    return @("mitv.service", "com.xiaomi.mitv.res", "com.android.tv.settings", "com.duokan.factorytest",
        "com.android.vending", "com.google.android.apps.mediashell", "com.google.android.katniss",
        "com.google.android.tv.assistant", "com.google.android.gms", "com.google.android.inputmethod.latin",
        $script:TvosPkg, "com.jhonatan.milauncher", "android", "com.android.systemui") -contains $Pkg
}

# Paquetes de una lista (sin comentarios ni líneas vacías).
function Read-List([string]$Path) {
    return @(Get-Content -Encoding UTF8 $Path | ForEach-Object { ($_ -replace "#.*", "").Trim() } | Where-Object { $_ -ne "" })
}

# Estado de un paquete: enabled, disabled o missing.
function Get-PkgState([string]$Pkg) {
    if (((Sh "pm path $Pkg") -join "").Trim() -eq "") { return "missing" }
    if ((Sh "pm list packages -d") -contains "package:$Pkg") { return "disabled" }
    return "enabled"
}

$script:AnimKeys = @("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
