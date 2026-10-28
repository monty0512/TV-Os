# TV-Os · Deshace cleanup: vuelve cada app, restricción y animación al estado guardado en el respaldo.
# Uso: .\cleanup-restore.ps1 [IP-de-la-TV] [respaldo]   (sin respaldo usa el más reciente de reports\)
param([string]$Target = "", [string]$Backup = "")
. (Join-Path $PSScriptRoot "common.ps1")

if ($Backup -eq "") {
    $last = Get-ChildItem (Join-Path $PSScriptRoot "reports") -Filter "limpieza-respaldo-*.txt" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($last) { $Backup = $last.FullName }
}
if ($Backup -eq "" -or -not (Test-Path $Backup)) {
    Write-Host "ERROR: no se encontró ningún respaldo en reports\. Indica la ruta: .\cleanup-restore.ps1 192.168.1.50 C:\ruta\respaldo.txt"
    exit 1
}

Connect-Tv $Target

Write-Host "Se va a restaurar en la TV el estado guardado en: $Backup"
Write-Host "  - Reactivar las apps que estaban activas antes de la limpieza"
Write-Host "  - Quitar las restricciones de segundo plano"
Write-Host "  - Volver las animaciones a su valor anterior"
$ok = Read-Host "¿Continuar? (s/N)"
if ($ok -notmatch "^(s|si|sí)$") { Write-Host "Cancelado. No se cambió nada."; exit 0 }

foreach ($line in Get-Content -Encoding UTF8 $Backup) {
    $kind, $a, $b = ($line.Trim() -split "\s+", 3)
    switch ($kind) {
        "anim" {
            if ($b -eq "null" -or $b -eq "") { Sh "settings delete global $a" | Out-Null } else { Sh "settings put global $a $b" | Out-Null }
            Write-Host "OK: $a = $b"
        }
        "pkg" {
            if ($b -eq "enabled") { Sh "pm enable $a" | Out-Null; Write-Host "OK: reactivada $a" }
        }
        "appops" {
            Sh "cmd appops set $a RUN_ANY_IN_BACKGROUND $b" | Out-Null
            Write-Host "OK: $a segundo plano = $b"
        }
        "bucket" {
            if ($b -match "^\d+$") { Sh "am set-standby-bucket $a $b" | Out-Null }
        }
    }
}
Write-Host "Listo. Reinicia la TV."
