# TV-Os · Mide la memoria del Inicio (objetivo Fase 1: < 60 MB PSS). Solo lectura.
param([string]$Target = "")
. (Join-Path $PSScriptRoot "common.ps1")
Connect-Tv $Target
$LimitKb = 61440

Sh "input keyevent KEYCODE_HOME" | Out-Null
Write-Host "Esperando 8 s a que el Inicio se estabilice…"
Start-Sleep -Seconds 8
$out = Sh "dumpsys meminfo $script:TvosPkg"
$pss = $null
$line = $out | Where-Object { $_ -match "TOTAL PSS:\s+(\d+)" } | Select-Object -First 1
if ($line -and $line -match "TOTAL PSS:\s+(\d+)") { $pss = [int]$Matches[1] }
if (-not $pss) {
    $line = $out | Where-Object { $_ -match "^\s+TOTAL\s+(\d+)" } | Select-Object -First 1
    if ($line -and $line -match "^\s+TOTAL\s+(\d+)") { $pss = [int]$Matches[1] }
}
if (-not $pss) { Write-Host "No se pudo leer la memoria. ¿Está TV-Os abierto?"; exit 1 }

Write-Host ("TV-Os usa {0} MB (PSS) · límite {1} MB" -f [math]::Round($pss / 1024), ($LimitKb / 1024))
if ($pss -le $LimitKb) { Write-Host "RESULTADO: dentro del presupuesto" } else { Write-Host "RESULTADO: por encima del presupuesto" }
Write-Host ""
$out | Where-Object { $_ -match "Java Heap|Native Heap|Code:|Graphics:|TOTAL" } | Select-Object -First 8 | ForEach-Object { Write-Host $_ }
