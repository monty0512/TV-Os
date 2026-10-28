# TV-Os · Vuelve a poner MiLauncher como Inicio (no desinstala TV-Os).
param([string]$Target = "")
. (Join-Path $PSScriptRoot "common.ps1")
Connect-Tv $Target
Sh "cmd package set-home-activity $script:BackupHome" | Out-Null
Sh "input keyevent KEYCODE_HOME" | Out-Null
Write-Host ("Inicio actual: " + (Get-CurrentHome))
