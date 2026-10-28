# Kit ADB de TV-Os

Scripts para preparar la TV desde un PC.

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

## Instalar TV-Os (Fase 1)
Compila primero el APK (ver el README principal). Luego, desde esta carpeta:

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File .\install.ps1 192.168.1.50
```
```bash
# Linux / macOS
./install.sh 192.168.1.50
```
El script muestra lo que va a hacer y pide confirmación. Instala el APK, pone TV-Os como Inicio, **deja MiLauncher instalado** como respaldo y activa el permiso de apagado (temporizador y botón Apagar del Centro de control).

> Para **desinstalar** TV-Os, primero quita ese permiso en TV-Os › Personalizar › "Desactivar apagado".

Volver a MiLauncher en cualquier momento:
```powershell
powershell -ExecutionPolicy Bypass -File .\restore-home.ps1 192.168.1.50
```

## Medir la memoria (solo lectura)
```powershell
powershell -ExecutionPolicy Bypass -File .\memory.ps1 192.168.1.50
```
Objetivo: **menos de 70 MB PSS** con el Inicio en pantalla. El script también mide TV-Os en segundo plano (abre YouTube unos segundos): ahí debe bajar porque se liberan los banners.

## Archivos
| Archivo | Uso |
|---|---|
| `diagnose.*` | Diagnóstico de solo lectura |
| `install.*` | Instala TV-Os y lo pone como Inicio |
| `restore-home.*` | Vuelve a MiLauncher como Inicio |
| `memory.*` | Mide la memoria de TV-Os |
| `launch-time.*` | Mide cuánto tarda en abrir Netflix y Disney+ desde cero |
| `common.*` | Funciones compartidas |
| `debloat-list.txt` | Lista de limpieza (se aplica en la Fase 4) |

## Medir la apertura de apps
Compara el tiempo de arranque en frío con TV-Os y con MiLauncher:
```powershell
powershell -ExecutionPolicy Bypass -File .\launch-time.ps1 192.168.1.128
powershell -ExecutionPolicy Bypass -File .\restore-home.ps1 192.168.1.128
powershell -ExecutionPolicy Bypass -File .\launch-time.ps1 192.168.1.128
powershell -ExecutionPolicy Bypass -File .\install.ps1 192.168.1.128
```
