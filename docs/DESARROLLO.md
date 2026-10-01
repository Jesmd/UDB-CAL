# Desarrollo

Notas para quien quiera compilar, probar o modificar MinimoApp.

## Privacidad en detalle

- El inicio de sesión ocurre en la página oficial, dentro de un WebView.
- El único permiso es Internet. No hay copias de seguridad en la nube ni transferencia entre dispositivos.
- No se escriben en los logs cookies, tokens, HTML ni notas.
- El WebView solo navega a `admacad.udb.edu.sv` y `portal.udb.edu.sv`, por HTTPS. No ejecuta JavaScript en la página de login. En las páginas posteriores solo lee el DOM y pulsa los botones NF.
- "Cerrar sesión", en Ajustes, borra las cookies, el almacenamiento y la caché del WebView.

## Diseño

La interfaz imita el "Liquid Glass" de iOS y está hecha solo con Compose. Los componentes están en `ui/glass/` y la mascota en `ui/mascot/`.

El vidrio se dibuja en capas (`GlassSurface.kt`): una sombra suave, lo que hay detrás (desenfocado y más vivo), un cuerpo translúcido, el tinte y la luz del borde. Según la versión de Android:

- Android 13 o superior: dos shaders AGSL (`GlassShaders.kt`). Uno dobla lo que hay detrás a lo largo del borde, como el canto de una lente, con un leve arcoíris en los colores. El otro dibuja el brillo del borde según hacia dónde mira cada tramo, más fuerte donde da la luz (arriba a la izquierda). Solo las barras y los botones flotantes tienen "detrás" (`glassSource`); las tarjetas viven dentro del contenido y no pueden doblar nada.
- Android 12: desenfoque, y el brillo del borde se dibuja con degradados.
- Anteriores: relleno esmerilado más opaco.

Si un shader no compila en un teléfono, `GlassShaders` devuelve `null` y esa pieza se dibuja con la versión de Android 12.

La imagen `docs/mimo.png` se genera con `MimoRenderTest.rendersLogoForReadme`.

## Cómo se calcula

- El acumulado es `A = Σ nota·peso/100` sobre lo calificado. El pendiente es `P = Σ peso/100` sobre lo que falta.
- Para una nota objetivo `T` (aprobar) o `G` (meta), el promedio necesario en lo pendiente es `X = (T − A) / P`, redondeado hacia arriba a 2 decimales.
- El máximo posible es `A + 10·P` y la nota final proyectada es `A + s·P`. Ambos se muestran truncados a 2 decimales, y el veredicto usa el valor exacto.
- Una actividad del portal con 0.00 cuenta como pendiente, salvo que marques "Es un 0 real".
- Los cálculos usan `BigDecimal` y están en `domain/GradeCalculator.kt`. La política de redondeo está en `domain/RoundingPolicy.kt`.

## Compilar

Necesitas JDK 17 o superior y el SDK de Android con la plataforma 37. Android Studio lo instala solo.

```
./gradlew assembleDebug testDebugUnitTest
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions compila el APK y corre los tests en cada push a `main` y en cada PR. Para bajar el APK, abre la ejecución del workflow y descarga el artefacto `minimo-debug-apk`.

Los tests de pantalla usan Robolectric, que descarga archivos de Android la primera vez que corre. Si Maven Central responde con error 429, vuelve a ejecutarlos unos minutos después.

## Instalar y actualizar

- Con el teléfono conectado y la depuración USB activa: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Sin computadora: abre el APK en el teléfono y permite instalar apps desconocidas a la app desde la que lo abres.
- Para actualizar, instala el APK nuevo encima del anterior. Los datos se conservan.

Cada computadora, y también GitHub Actions, firma los APK de depuración con una clave distinta. Android no deja instalar un APK encima de otro firmado con otra clave, y desinstalar para cambiar de origen borra tus datos. Instala siempre APK del mismo origen.

## Capturas del portal y lector

El lector del portal (`data/portal/GradesParser.kt`) se escribió solo a partir de capturas reales, guardadas en `app/src/test/resources/fixtures/`:

- `notas_sin_detalle.html` es la página de Notas sin ningún detalle abierto.
- `notas_detalle_dmd104.html` es la misma página con el detalle de una materia abierto.

### Hacer una captura nueva

1. En Ajustes, activa el modo diagnóstico.
2. Pulsa "Abrir portal", luego **Notas** y, si hace falta, el botón **NF** de una materia.
3. Pulsa "Guardar HTML de esta página" y elige dónde guardarlo. El token antifalsificación se guarda como `REDACTED`.

### Probarla en el teléfono

Con el modo diagnóstico activo, "Probar el lector con un HTML guardado" (en Ajustes) muestra lo que la app entiende de la página: el ciclo, las materias y el detalle abierto. Si la página no tiene la estructura esperada, lo dice. No cambia tus datos.

### Agregarla a los tests

1. Anonimízala. Reemplaza tu nombre, carnet, foto (`ObtenerImagenP/<número>`), carrera, campus, CUM, avance y cualquier correo o teléfono. Deja materias, actividades, porcentajes y notas como están. No subas capturas sin anonimizar, porque el repositorio puede ser público.
2. Cópiala a `app/src/test/resources/fixtures/` y agrega o ajusta los tests en `app/src/test/java/com/example/minimo/data/portal/`.
3. Ejecuta `./gradlew testDebugUnitTest`.

Si el portal cambia y la sincronización muestra "El portal cambió", haz una captura nueva, compruébala con "Probar el lector" y actualiza el lector contra ella.

## Estructura

```
app/src/main/java/com/example/minimo/
  domain/        Kotlin puro: Course, Evaluation, GradeCalculator, RoundingPolicy, validación
  data/          CourseRepository y almacenamiento local (DataStore con un snapshot JSON)
  data/portal/   Sesión, URLs permitidas, lector (Jsoup) y sincronización con el portal
  ui/            Pantallas Compose y un ViewModel por pantalla
  ui/glass/      Componentes de vidrio: superficies, botones, barras, diálogos
  ui/mascot/     MIMO, la mascota
```

Usa Kotlin, Jetpack Compose con Material 3, Navigation Compose, ViewModel y StateFlow, Coroutines, kotlinx.serialization, DataStore y Jsoup. Las versiones están en `gradle/libs.versions.toml`.
