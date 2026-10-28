# TV-Os · Decisiones aprobadas

Diseño de muestra aprobado (8 pantallas): https://claude.ai/artifact/CbV4gxiAQKc1BHbkJLMdrh

## Dispositivo objetivo
- Xiaomi `MiTV-MOOQ0`, Android 11, ~2 GB de RAM (pendiente de confirmar con el diagnóstico de la Fase 0).

## Producto
| Tema | Decisión |
|---|---|
| Enfoque | Launcher propio + kit ADB sin root (sin ROM) |
| Estilo | Apple TV clásico: Top Shelf + fila de apps con efecto parallax |
| Top Shelf | Contenido de la app enfocada; si no publica nada, fondo con el color del icono y nombre grande |
| Personalización | Ordenar/ocultar apps, tema (oscuro/claro/automático) + acento, tamaño (5 o 7 por fila) |
| Perfiles | Selector al encender; cada perfil con su fila de apps y su fondo |
| Fondos | Solo desde el móvil: QR + página web local en la red Wi-Fi de casa |
| Salvapantallas | Google Backdrop con reloj de TV-Os encima |
| Centro de control | Pulsación larga en Inicio: perfil, entrada, Wi-Fi, temporizador, modo de imagen, volumen |
| Rendimiento | Adaptativo según memoria disponible |
| Bienvenida | Crear perfiles → apps favoritas → tema y tamaño |
| Accesibilidad | Español/inglés, texto grande, alto contraste |
| Clima | Ubicación automática por IP (Open-Meteo); ciudad manual como respaldo |
| Voz | Se mantiene el Asistente de Google |
| Continuar viendo | Contenido publicado por las apps + apps recientes |
| Limpieza | Equilibrada: PatchWall, anuncios, telemetría y apps de Xiaomi; se conservan Play Store, Chromecast y Asistente |
| Instalación y actualización | Solo con el script de PC (APK + permisos + limpieza) |
| Nombre | TV-Os |

## Arquitectura
- Kotlin + Jetpack Compose for TV, `minSdk 30` (Android 11).
- DataStore (ajustes) + Room (perfiles). Sin servicios en la nube.
- Módulos: `home`, `controlcenter`, `profiles`, `settings`, `onboarding`, `wallpaper`, `weather`, `perf`.
- `tools/adb-kit/`: diagnóstico, instalación, limpieza y restauración.

## Riesgos conocidos
- `READ_TV_LISTINGS` se concede por ADB (`pm grant`); su suficiencia para leer canales de otras apps está probada por la comunidad, no documentada por Google. Se verifica en la TV real.
- Sin root no se pueden usar `ACCESS_ALL_EPG_DATA` ni `ACCESS_WATCHED_PROGRAMS` (signature|privileged).
- Desde el 31/03/2025 Google Fotos no permite leer la biblioteca a apps de terceros; por eso el salvapantallas usa Backdrop.

## Fases
0. Diagnóstico de solo lectura ← **actual**
1. Inicio, ordenar/ocultar apps, apariencia
2. Perfiles, bienvenida, Centro de control
3. Top Shelf + Continuar viendo, fondo desde el móvil, clima
4. Kit ADB de limpieza completa con opción de deshacer
