# TV-Os

Launcher inmersivo al estilo Apple TV para televisores Android TV (objetivo inicial: Xiaomi MiTV-MOOQ0, Android 10), con un kit ADB para quitar software innecesario y mejorar la velocidad **sin root**.

- Decisiones y arquitectura: [`docs/decisiones.md`](docs/decisiones.md)
- Kit ADB (diagnóstico, instalación, memoria): [`tools/adb-kit/`](tools/adb-kit/README.md)

## Estado
**v1.0.0:** Inicio con carrusel por app y cajón de apps, ordenar/ocultar, apariencia, perfiles con fondo y foto desde el móvil, bienvenida, Centro de control (mantener ≡ o SET), clima con ciudad automática o manual, y kit ADB de instalación y limpieza reversible.

## Compilar (Android Studio)
1. **File › Open…** y elige la carpeta del repositorio. Espera a que termine la sincronización de Gradle.
2. Selecciona la variante **release**: **Build › Select Build Variant…** › `app` = `release`.
   (Release usa R8: arranca más rápido y usa menos memoria.)
3. Clave propia (una sola vez, recomendado para la v1.0):
   - **Build › Generate Signed App Bundle / APK… › APK › Create new…** y guarda `tvos-release.jks` en la raíz del repositorio. Cancela el asistente después de crear la clave.
   - Copia `keystore.properties.example` como `keystore.properties` y rellena las contraseñas.
   - Guarda una copia de seguridad del `.jks` y de las contraseñas: sin ellas no se puede actualizar TV-Os sin desinstalarlo.
   - Sin `keystore.properties` se firma con la clave de depuración, como antes.
4. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
   El APK queda en `app/build/outputs/apk/release/app-release.apk`.
5. Instálalo con el kit ADB: `tools/adb-kit/install.ps1` (Windows) o `install.sh` (Linux/macOS).

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
