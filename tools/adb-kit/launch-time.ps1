# TV-Os · Mide cuánto tarda en abrir una app desde cero (arranque en frío). Solo lectura salvo
# que cierra la app medida antes de abrirla (force-stop), igual que si la TV se acabara de encender.
# Uso: .\launch-time.ps1 [IP] [paquete ...]   (por defecto: Netflix y Disney+)
param([string]$Target = "", [Parameter(ValueFromRemainingArguments = $true)][string[]]$Packages)
. (Join-Path $PSScriptRoot "common.ps1")
Connect-Tv $Target
if (-not $Packages -or $Packages.Count -eq 0) { $Packages = @("com.netflix.ninja", "com.disney.disneyplus") }

Write-Host ("Inicio actual: " + (Get-CurrentHome))
foreach ($pkg in $Packages) {
    $comp = ((Sh "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER $pkg") | Select-Object -Last 1).Trim()
    if ($comp -notmatch "/") { Write-Host "${pkg}: no instalada"; continue }
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    Start-Sleep -Seconds 3
    Sh "am force-stop $pkg" | Out-Null
    Start-Sleep -Seconds 2
    $out = Sh "am start -W -n $comp"
    $total = ($out | Where-Object { $_ -match "^TotalTime:\s+(\d+)" } | ForEach-Object { $Matches[1] }) | Select-Object -First 1
    $wait = ($out | Where-Object { $_ -match "^WaitTime:\s+(\d+)" } | ForEach-Object { $Matches[1] }) | Select-Object -First 1
    Write-Host ("{0}: TotalTime {1} ms · WaitTime {2} ms" -f $pkg, $total, $wait)
    Start-Sleep -Seconds 6
}
Sh "input keyevent KEYCODE_HOME" | Out-Null
