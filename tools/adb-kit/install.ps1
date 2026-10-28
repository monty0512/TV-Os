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
Write-Host ("     (APK compilado el " + (Get-Item $Apk).LastWriteTime.ToString("yyyy-MM-dd HH:mm") + ")")
Write-Host ("  2. Poner TV-Os como Inicio (antes: " + (Get-CurrentHome) + ")")
Write-Host "  3. Mantener MiLauncher instalado como respaldo"
Write-Host "  4. Activar el servicio de accesibilidad de TV-Os (apagar pantalla y botón ≡ desde cualquier app)"
$ok = Read-Host "¿Continuar? (s/N)"
if ($ok -notmatch "^(s|si|sí)$") { Write-Host "Cancelado. No se cambió nada."; exit 0 }

Write-Host "Instalando…"
& $script:Adb @script:AdbArgs install -r "$Apk"
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: falló la instalación."; exit 1 }

$ver = (Sh "dumpsys package $script:TvosPkg") | Where-Object { $_ -match "versionName=" } | Select-Object -First 1
Write-Host ("Versión instalada: " + ($ver -replace ".*versionName=", ""))
Write-Host "Poniendo TV-Os como Inicio…"
Sh "cmd package set-home-activity $script:TvosHome" | Out-Null
$now = Get-CurrentHome
if ($now -eq $script:TvosHome -or $now -eq "$script:TvosPkg/$script:TvosPkg.MainActivity") {
    Write-Host "OK: Inicio = $now"
} else {
    Write-Host "AVISO: el Inicio sigue siendo '$now'. Pulsa Inicio en el mando y elige TV-Os con 'Siempre'."
}
Write-Host "Activando el servicio de accesibilidad de TV-Os…"
$cur = ((Sh "settings get secure enabled_accessibility_services") -join "").Trim()
if ($cur -like "*$script:TvosA11y*") { $new = $cur }
elseif ($cur -eq "" -or $cur -eq "null") { $new = $script:TvosA11y }
else { $new = "${cur}:$script:TvosA11y" }   # conserva otros servicios ya activos
Sh "settings put secure enabled_accessibility_services $new" | Out-Null
Sh "settings put secure accessibility_enabled 1" | Out-Null
Start-Sleep -Seconds 2
if (((Sh "dumpsys accessibility") -join "`n") -match "TvOsAccessibilityService") {
    Write-Host "OK: accesibilidad activada"
} else {
    Write-Host "AVISO: el sistema no cargó el servicio de accesibilidad."
}
Sh "input keyevent KEYCODE_HOME" | Out-Null
Write-Host ""
Write-Host "Listo. Para volver a MiLauncher: .\restore-home.ps1 $Target"
