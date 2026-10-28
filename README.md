# TV-Os

Launcher inmersivo al estilo Apple TV para televisores Android TV (objetivo inicial: Xiaomi MiTV-MOOQ0, Android 10), con un kit ADB para quitar software innecesario y mejorar la velocidad **sin root**.

- Decisiones y arquitectura: [`docs/decisiones.md`](docs/decisiones.md)
- Kit ADB (diagnóstico, instalación, memoria): [`tools/adb-kit/`](tools/adb-kit/README.md)

## Estado
**Fase 1:** Inicio con Top Shelf y cajón de apps en cuadrícula (5/6/7 columnas), ordenar/ocultar apps en 4 direcciones y apariencia.

## Compilar (Android Studio)
1. **File › Open…** y elige la carpeta del repositorio. Espera a que termine la sincronización de Gradle.
2. Selecciona la variante **release**: **Build › Select Build Variant…** › `app` = `release`.
   (Release usa R8: arranca más rápido y usa menos memoria; se firma con la clave de depuración, válido para instalar por ADB.)
3. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
   El APK queda en `app/build/outputs/apk/release/app-release.apk`.
4. Instálalo con el kit ADB: `tools/adb-kit/install.ps1` (Windows) o `install.sh` (Linux/macOS).

## Estructura
```text
app/src/main/java/com/jhonatan/tvos/
├── MainActivity.kt          # Actividad HOME (botón Inicio del mando)
├── data/                    # Apps instaladas y ajustes (DataStore)
├── perf/                    # Modo adaptativo (low-RAM → efectos reducidos)
└── ui/
    ├── TvOsRoot.kt          # Navegación entre pantallas
    ├── home/                # Top Shelf + fila de apps
    ├── edit/                # Ordenar y ocultar apps
    ├── settings/            # Apariencia y accesibilidad
    ├── components/          # Tiles y botones para el mando
    └── theme/               # Colores, acento, texto grande, alto contraste
```
