# TV-Os · Captura qué teclas recibe TV-Os y qué apps se abren (y cuántas veces). Solo lectura.
# Uso: .\logs.ps1 [IP]  → reproduce el problema en la TV y pulsa Enter.
param([string]$Target = "")
. (Join-Path $PSScriptRoot "common.ps1")
Connect-Tv $Target
$dir = Join-Path $PSScriptRoot "reports"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$outFile = Join-Path $dir ("logs-{0}.txt" -f (Get-Date -Format "yyyyMMdd-HHmmss"))

& $script:Adb @script:AdbArgs logcat -c
Write-Host "Registro en marcha. En la TV:"
Write-Host "  1. Pulsa el botón ≡ y otros botones del mando estando en el Inicio de TV-Os."
Write-Host "  2. Abre Netflix desde TV-Os y espera 30 segundos aunque se quede en negro."
Read-Host "Cuando termines, pulsa Enter aquí…" | Out-Null

$lines = & $script:Adb @script:AdbArgs logcat -d -v time | ForEach-Object { "$_".TrimEnd("`r") } |
    Where-Object { $_ -match "TvOs|START u0|Start proc|Displayed|ActivityTaskManager: (START|Force)|am_proc_died|Killing|lowmemorykiller|lmkd|has died|died|ANR|FATAL|[Nn]etflix" }
$lines | Set-Content -Path $outFile -Encoding UTF8

Write-Host ""
Write-Host "== Teclas que recibió TV-Os =="
$lines | Where-Object { $_ -match "TvOs.*key (.+)$" } | ForEach-Object { ($_ -replace ".*key ", "") } |
    Group-Object | ForEach-Object { Write-Host ("  {0} x{1}" -f $_.Name, $_.Count) }
Write-Host "== Apps abiertas por TV-Os =="
$lines | Where-Object { $_ -match "TvOs.*launch " } | ForEach-Object { Write-Host ("  " + ($_ -replace ".*launch ", "")) }
Write-Host "== Arranques de actividades (sistema) =="
$lines | Where-Object { $_ -match "START u0.*cmp=([^ }]+)" } | ForEach-Object { if ($_ -match "cmp=([^ }]+)") { Write-Host ("  " + $Matches[1]) } } | Select-Object -First 20
Write-Host ""
Write-Host "Informe completo: $outFile"
