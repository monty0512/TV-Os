# TV-Os · Limpieza (Fase 4): desactiva las apps de debloat-list.txt, restringe en segundo plano las de
# autostart-list.txt y pone las animaciones a 0.5. Guarda antes el estado en reports\ para deshacerlo con cleanup-restore.
# Uso: .\cleanup.ps1 [IP-de-la-TV]
param([string]$Target = "")
. (Join-Path $PSScriptRoot "common.ps1")

Connect-Tv $Target

$debloat = Read-List (Join-Path $PSScriptRoot "debloat-list.txt")
$autostart = Read-List (Join-Path $PSScriptRoot "autostart-list.txt")
foreach ($p in $debloat + $autostart) {
    if (Test-Protected $p) { Write-Host "ERROR: '$p' está en la lista de paquetes que nunca se tocan. Quítalo de la lista."; exit 1 }
}

Write-Host "Leyendo el estado actual…"
$outDir = Join-Path $PSScriptRoot "reports"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$backup = Join-Path $outDir ("limpieza-respaldo-{0}.txt" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$lines = @()
foreach ($k in $script:AnimKeys) { $lines += "anim $k " + ((Sh "settings get global $k") -join "").Trim() }
$states = [ordered]@{}
foreach ($p in $debloat) { $states[$p] = Get-PkgState $p; $lines += "pkg $p " + $states[$p] }
foreach ($p in $autostart) {
    $mode = "default"
    $m = (Sh "cmd appops get $p RUN_ANY_IN_BACKGROUND") -join "`n"
    if ($m -match "RUN_ANY_IN_BACKGROUND: ([a-z]+)") { $mode = $Matches[1] }
    $lines += "appops $p $mode"
    $lines += "bucket $p " + ((Sh "am get-standby-bucket $p") | Select-Object -Last 1).Trim()
}
$lines | Set-Content -Encoding UTF8 $backup

Write-Host ""
Write-Host "Se va a hacer en la TV:"
Write-Host "  1. Desactivar estas apps (se pueden reactivar con cleanup-restore):"
foreach ($p in $states.Keys) {
    switch ($states[$p]) {
        "enabled" { Write-Host "       $p" }
        "disabled" { Write-Host "       $p (ya estaba desactivada)" }
        "missing" { Write-Host "       $p (no está instalada, se omite)" }
    }
}
Write-Host "  2. Restringir en segundo plano (siguen funcionando al abrirlas):"
foreach ($p in $autostart) { Write-Host "       $p" }
Write-Host ("  3. Animaciones a 0.5 (ahora: " + (($lines[0] -split " ")[2]) + ")")
Write-Host "  Estado anterior guardado en: $backup"
$ok = Read-Host "¿Continuar? (s/N)"
if ($ok -notmatch "^(s|si|sí)$") { Write-Host "Cancelado. No se cambió nada."; exit 0 }

foreach ($p in $states.Keys) {
    if ($states[$p] -ne "enabled") { continue }
    if (((Sh "pm disable-user --user 0 $p") -join "") -match "disabled-user") { Write-Host "OK: desactivada $p" }
    else { Write-Host "AVISO: no se pudo desactivar $p" }
}
foreach ($p in $autostart) {
    if ((Get-PkgState $p) -eq "missing") { Write-Host "Omitida (no instalada): $p"; continue }
    Sh "cmd appops set $p RUN_ANY_IN_BACKGROUND ignore" | Out-Null
    Sh "am set-standby-bucket $p rare" | Out-Null
    if (((Sh "cmd appops get $p RUN_ANY_IN_BACKGROUND") -join "") -match "ignore") { Write-Host "OK: restringida $p" }
    else { Write-Host "AVISO: no se pudo restringir $p" }
}
foreach ($k in $script:AnimKeys) { Sh "settings put global $k 0.5" | Out-Null }
Write-Host ("OK: animaciones = " + ((Sh "settings get global window_animation_scale") -join "").Trim())
Write-Host ""
Write-Host "Listo. Reinicia la TV para notar el arranque más limpio."
Write-Host "Para deshacerlo: powershell -ExecutionPolicy Bypass -File .\cleanup-restore.ps1 $Target"
