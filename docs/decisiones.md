# TV-Os · Decisiones aprobadas

Diseño de muestra aprobado (10 pantallas): https://claude.ai/artifact/CbV4gxiAQKc1BHbkJLMdrh

## Dispositivo objetivo (confirmado con el diagnóstico de la Fase 0)
| Dato | Valor | Impacto |
|---|---|---|
| Modelo | Xiaomi `MiTV-MOOQ0` (nombre interno `croods`) | — |
| Android | **10 (API 29)**, parche 2022-01-05 | `minSdk 29` |
| SoC / ABI | MediaTek `m7632`, 4 núcleos, **solo 32 bits** (`armeabi-v7a`) | Sin librerías nativas de 64 bits |
| RAM | 1,8 GB, **`low_ram=true`**, ~550 MB libres, swap casi lleno | Modo adaptativo arranca en "reducido"; presupuesto de memoria estricto |
| Pantalla | 1920×1080, densidad 320 (xhdpi) | Diseño a 960×540 dp |
| Almacenamiento | 8,3 GB libres en `/data` | Sin problema |
| Launcher actual | `com.jhonatan.milauncher` (Google tvlauncher y leanback ya desactivados) | TV-Os lo reemplaza |
| Fallback HOME | `com.duokan.factorytest/.FactoryLauncher` | Nunca debe quedar como Inicio |
| Backdrop | Instalado y activo como salvapantallas | Salvapantallas cubierto |
| `READ_TV_LISTINGS` | Definido, `prot=dangerous` (se puede conceder por ADB) | Top Shelf viable; probar en Fase 3 |

## Producto
| Tema | Decisión |
|---|---|
| Enfoque | Launcher propio + kit ADB sin root (sin ROM) |
| Estilo | Apple TV clásico: Top Shelf + primera fila de favoritas; al bajar, cajón de apps en cuadrícula |
| Top Shelf | Contenido de la app enfocada; si no publica nada, fondo con el color del icono y nombre grande |
| Personalización | Ordenar/ocultar apps (4 direcciones), tema (oscuro/claro/automático) + acento, tamaño (5, 6 o 7 columnas) |
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

## Decisiones tras el diagnóstico
| Tema | Decisión |
|---|---|
| MiLauncher (`com.jhonatan.milauncher`) | Se conserva instalado como Inicio de respaldo |
| Interfaz | Compose for TV con presupuesto de memoria medido en la Fase 1; si no lo cumple, Views |
| Limpieza | Grupo seguro + actualizaciones Xiaomi + multimedia USB Xiaomi + compartir pantalla Xiaomi (`tools/adb-kit/debloat-list.txt`) |
| Liberar memoria | Botón en el Centro de control (`killBackgroundProcesses`) |

## Arquitectura
- Kotlin + Jetpack Compose for TV, `minSdk 29` (Android 10), solo `armeabi-v7a`/sin código nativo. Presupuesto de memoria: < 70 MB PSS en Inicio (medido 78 MB antes de optimizar; ~20 MB son búferes de pantalla 1080p, fijos). Banners RGB_565, liberados solo cuando Android pide memoria (onTrimMemory) con TV-Os en segundo plano.
- DataStore (ajustes) + Room (perfiles). Sin servicios en la nube.
- Módulos: `home`, `controlcenter`, `profiles`, `settings`, `onboarding`, `wallpaper`, `weather`, `perf`.
- `tools/adb-kit/`: diagnóstico, instalación, limpieza y restauración.

## Riesgos conocidos
- `READ_TV_LISTINGS` se concede por ADB (`pm grant`); su suficiencia para leer canales de otras apps está probada por la comunidad, no documentada por Google. Se verifica en la TV real.
- Sin root no se pueden usar `ACCESS_ALL_EPG_DATA` ni `ACCESS_WATCHED_PROGRAMS` (signature|privileged).
- Desde el 31/03/2025 Google Fotos no permite leer la biblioteca a apps de terceros; por eso el salvapantallas usa Backdrop.

## Fases
0. Diagnóstico de solo lectura ✅
1. Inicio, ordenar/ocultar apps, apariencia ✅
2. Perfiles, bienvenida, Centro de control ← **actual**
3. Top Shelf + Continuar viendo, fondo desde el móvil, clima
4. Kit ADB de limpieza completa con opción de deshacer

## Fase 2 (decisiones)
| Tema | Decisión |
|---|---|
| Abrir Centro de control | **Mantener ≡** (el mando no tiene botón de casa: ≡ envía `KEY_HOMEPAGE` → `KEYCODE_HOME`; corto = Inicio) o **SET** (`KEY_TASKMANAGER` → `KEYCODE_SETTINGS`), desde cualquier app vía accesibilidad. También el botón junto al reloj. Verificado con `getevent` |
| Perfil guarda | Fila de apps (orden y favoritas), apps ocultas, tema, acento, tamaño y accesibilidad. El fondo llega en la Fase 3 |
| Selector de perfiles | Al encender la TV (salir del reposo) si hay más de un perfil |
| Centro de control | Perfil, liberar memoria, temporizador (15/30/60/90), entradas, Wi-Fi, imagen, volumen y apagar |
| Apagar / temporizador | Servicio de accesibilidad (`GLOBAL_ACTION_LOCK_SCREEN`), activado por `install`. El firmware de Xiaomi descarta el administrador de dispositivo (verificado) |
| Entradas HDMI | `TvInputManager` + Live TV del fabricante. Por verificar en el MiTV |
| Primera apertura lenta tras encender | Carga de arranque del sistema (verificado en logcat), no TV-Os. Fase 4: bloquear arranque automático de apps (AnyDesk, Twitch, Amazon…) |
