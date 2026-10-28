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
