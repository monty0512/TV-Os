# TV-Os · Mide la memoria del Inicio en pantalla y en segundo plano. No modifica nada.
# Objetivo: < 70 MB PSS en pantalla; en segundo plano debe bajar (banners liberados).
# Abre YouTube (o Play Store) unos segundos y vuelve al Inicio.
param([string]$Target = "")
. (Join-Path $PSScriptRoot "common.ps1")
Connect-Tv $Target
$LimitKb = 71680

function Get-PssKb {
    $out = Sh "dumpsys meminfo $script:TvosPkg"
    $out | Where-Object { $_ -match "Java Heap:|Native Heap:|Code:|Graphics:" } | Select-Object -First 4 | ForEach-Object { Write-Host $_ }
    foreach ($pattern in @("TOTAL PSS:\s+(\d+)", "^\s+TOTAL\s+(\d+)")) {
        $line = $out | Where-Object { $_ -match $pattern } | Select-Object -First 1
        if ($line -and $line -match $pattern) { return [int]$Matches[1] }
    }
    return $null
}

Sh "input keyevent KEYCODE_HOME" | Out-Null
Write-Host "1/2 · Inicio en pantalla (esperando 8 s)…"
Start-Sleep -Seconds 8
$fg = Get-PssKb
if (-not $fg) { Write-Host "No se pudo leer la memoria. ¿Está TV-Os instalado como Inicio?"; exit 1 }

# Una app a pantalla completa (los Ajustes de Android TV son un panel y no ocultan el Inicio).
$bgPkg = "com.google.android.youtube.tv"
if (-not ((Sh "pm list packages") -contains "package:$bgPkg")) { $bgPkg = "com.android.vending" }
Write-Host "2/2 · TV-Os en segundo plano: se abre $bgPkg (esperando 10 s)…"
Sh "monkey -p $bgPkg -c android.intent.category.LEANBACK_LAUNCHER 1" | Out-Null
Start-Sleep -Seconds 10
$bg = Get-PssKb
Sh "input keyevent KEYCODE_HOME" | Out-Null

Write-Host ""
Write-Host ("En pantalla:       {0} MB · límite {1} MB" -f [math]::Round($fg / 1024), ($LimitKb / 1024))
if ($bg) { Write-Host ("En segundo plano:  {0} MB" -f [math]::Round($bg / 1024)) }
if ($fg -le $LimitKb) { Write-Host "RESULTADO: dentro del presupuesto" } else { Write-Host "RESULTADO: por encima del presupuesto" }
