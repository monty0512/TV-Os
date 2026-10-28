# Kit ADB de TV-Os

Scripts para preparar la TV desde un PC. **Fase 0: solo diagnóstico (no modifica nada).**

## Requisitos
1. [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) (`adb`) en el PATH.
2. En la TV: **Ajustes › Preferencias del dispositivo › Información** › pulsa 7 veces **Compilación** para activar las opciones de desarrollador.
3. En **Opciones de desarrollador**: activa **Depuración USB** (y **Depuración de red** si conectas por Wi-Fi).
4. La primera vez, acepta en la TV el aviso de autorización del PC.

## Diagnóstico (solo lectura)

Linux / macOS:
```bash
./diagnose.sh                # TV conectada por USB o ya emparejada
./diagnose.sh 192.168.1.50   # TV por Wi-Fi (puerto 5555)
```

Windows (PowerShell):
```powershell
.\diagnose.ps1
.\diagnose.ps1 192.168.1.50
```
Si PowerShell bloquea el script: `powershell -ExecutionPolicy Bypass -File .\diagnose.ps1`

El informe se guarda en `reports/diagnostico-<fecha>.txt` (excluido de git porque contiene datos de tu dispositivo).

## Qué comprueba
| Sección | Para qué sirve en TV-Os |
|---|---|
| Dispositivo, memoria, almacenamiento | Confirmar Android 11 / RAM y ajustar el modo adaptativo |
| Launcher actual y candidatos HOME | Saber qué launcher reemplazar (PatchWall / Google) |
| Componentes necesarios | Proveedor de TV, Backdrop, Play Store, Asistente |
| Permiso `READ_TV_LISTINGS` | Viabilidad del Top Shelf y "Continuar viendo" |
| Salvapantallas y animaciones | Valores actuales antes de optimizar |
| Paquetes del fabricante | Base para la lista de limpieza equilibrada (Fase 4) |
