# TV-Os · Deshace cleanup: vuelve cada app, restricción y animación al estado guardado en el respaldo.
# Uso: .\cleanup-restore.ps1 [IP-de-la-TV] [respaldo]
# Sin respaldo aplica todos los de reports\ del más nuevo al más antiguo: el más antiguo (estado original) gana.
param([string]$Target = "", [string]$Backup = "")
. (Join-Path $PSScriptRoot "common.ps1")

if ($Backup -ne "") { $backups = @($Backup) }
else {
    $backups = @(Get-ChildItem (Join-Path $PSScriptRoot "reports") -Filter "limpieza-respaldo-*.txt" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName })
}
if ($backups.Count -eq 0 -or -not (Test-Path $backups[0])) {
    Write-Host "ERROR: no se encontró ningún respaldo en reports\. Indica la ruta: .\cleanup-restore.ps1 192.168.1.50 C:\ruta\respaldo.txt"
    exit 1
}

Connect-Tv $Target

Write-Host "Se va a restaurar en la TV el estado guardado en:"
foreach ($b in $backups) { Write-Host "    $b" }
Write-Host "  - Reactivar las apps que estaban activas antes de la limpieza"
Write-Host "  - Quitar las restricciones de segundo plano"
Write-Host "  - Volver las animaciones a su valor anterior"
$ok = Read-Host "¿Continuar? (s/N)"
if ($ok -notmatch "^(s|si|sí)$") { Write-Host "Cancelado. No se cambió nada."; exit 0 }

foreach ($line in ($backups | ForEach-Object { Get-Content -Encoding UTF8 $_ })) {
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
