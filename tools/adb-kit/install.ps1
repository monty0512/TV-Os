# TV-Os · Instala el APK y lo deja como Inicio. MiLauncher queda instalado como respaldo.
# Uso: .\install.ps1 [IP-de-la-TV] [ruta-al-apk]
param([string]$Target = "", [string]$Apk = "")
. (Join-Path $PSScriptRoot "common.ps1")

if ($Apk -eq "") {
    $root = Join-Path $PSScriptRoot "..\..\app\build\outputs\apk"
    foreach ($c in @("release\app-release.apk", "debug\app-debug.apk")) {
        $p = Join-Path $root $c
        if (Test-Path $p) { $Apk = (Resolve-Path $p).Path; break }
    }
}
if ($Apk -eq "" -or -not (Test-Path $Apk)) {
    Write-Host "ERROR: no se encontró el APK. Compílalo en Android Studio (Build > Build APK(s))"
    Write-Host "       o indica la ruta: .\install.ps1 192.168.1.50 C:\ruta\app.apk"
    exit 1
}

Connect-Tv $Target

Write-Host "Se va a hacer en la TV:"
Write-Host "  1. Instalar/actualizar TV-Os: $Apk"
Write-Host ("  2. Poner TV-Os como Inicio (antes: " + (Get-CurrentHome) + ")")
Write-Host "  3. Mantener MiLauncher instalado como respaldo"
$ok = Read-Host "¿Continuar? (s/N)"
if ($ok -notmatch "^(s|si|sí)$") { Write-Host "Cancelado. No se cambió nada."; exit 0 }

Write-Host "Instalando…"
& $script:Adb @script:AdbArgs install -r "$Apk"
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: falló la instalación."; exit 1 }

Write-Host "Poniendo TV-Os como Inicio…"
Sh "cmd package set-home-activity $script:TvosHome" | Out-Null
$now = Get-CurrentHome
if ($now -eq $script:TvosHome -or $now -eq "$script:TvosPkg/$script:TvosPkg.MainActivity") {
    Write-Host "OK: Inicio = $now"
} else {
    Write-Host "AVISO: el Inicio sigue siendo '$now'. Pulsa Inicio en el mando y elige TV-Os con 'Siempre'."
}
Sh "input keyevent KEYCODE_HOME" | Out-Null
Write-Host ""
Write-Host "Listo. Para volver a MiLauncher: .\restore-home.ps1 $Target"
