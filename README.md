# Mínimo

App Android personal que lee tus notas del portal estudiantil y calcula cuánto necesitas sacar en lo que falta para aprobar una materia o alcanzar tu meta. Local, sin servidores y sin guardar tu contraseña.

No es una app oficial de la universidad.

## Qué hace

- **Materias:** lista con el acumulado y el porcentaje pendiente de cada una.
- **Detalle:** evaluaciones con peso y nota, meta por materia, "Calcular mínimo" (para aprobar y para la meta), máximo posible y simulador "¿qué pasa si saco X?".
- **Modo manual:** crear, editar y borrar materias y evaluaciones. Funciona sin portal.
- **Sincronizar:** trae las materias, actividades, porcentajes y notas del ciclo actual desde el portal, con tu propia sesión.

## Privacidad

- El inicio de sesión ocurre en la página oficial dentro de un WebView. La app nunca ve ni guarda tu contraseña.
- Todo se guarda solo en el teléfono: sin servidor, sin cuentas, sin nube, sin analytics, sin notificaciones.
- Solo pide el permiso de Internet. Sin copias de seguridad en la nube ni transferencia entre dispositivos.
- No escribe logs con cookies, tokens, HTML ni notas.
- El WebView solo navega a `admacad.udb.edu.sv` y `portal.udb.edu.sv` por HTTPS. No ejecuta JavaScript en la página de login; en las páginas posteriores solo lee el DOM y pulsa los botones NF.
- "Cerrar sesión" (en Ajustes) borra cookies, almacenamiento y caché del WebView.
- Solo sincroniza cuando pulsas "Sincronizar".

## Reglas de cálculo

- Acumulado `A = Σ nota·peso/100` de lo calificado; pendiente `P = Σ peso/100` de lo pendiente.
- Para una nota objetivo `T` (aprobar) o `G` (meta): `X = (T − A) / P`, redondeado **hacia arriba** a 2 decimales.
- Máximo posible `A + 10·P` y nota final proyectada `A + s·P`, mostrados **truncados** a 2 decimales. El veredicto usa el valor exacto.
- Una actividad del portal con 0.00 cuenta como **pendiente**, salvo que marques "Es un 0 real".
- Los cálculos usan `BigDecimal` y viven en `domain/GradeCalculator.kt`. La política de redondeo está en `domain/RoundingPolicy.kt`.

## Compilar

Requisitos: JDK 17 o superior y el SDK de Android con la plataforma 37 (Android Studio lo instala solo).

```
./gradlew assembleDebug testDebugUnitTest
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Cada push a `main` y cada PR compila el APK y corre los tests en GitHub Actions. El APK se puede descargar como artefacto `minimo-debug-apk` desde la ejecución del workflow.

## Instalar y actualizar

- Con el teléfono conectado y la depuración USB activa: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Sin computadora: abre el APK en el teléfono y permite "instalar apps desconocidas" para la app desde la que lo abres.
- Para actualizar, instala el APK nuevo encima del anterior: los datos se conservan.

**Importante:** cada computadora (y GitHub Actions) firma los APK de depuración con una clave distinta. Android no deja instalar encima un APK firmado con otra clave. Desinstalar para cambiar de origen borra tus datos, así que instala siempre APK del mismo origen.

## Capturas del portal (fixtures) y el lector

El lector del portal (`data/portal/GradesParser.kt`) se escribió solo contra capturas reales, guardadas en `app/src/test/resources/fixtures/`:

- `notas_sin_detalle.html`: la página de Notas sin ningún detalle abierto.
- `notas_detalle_dmd104.html`: la misma página con el detalle de una materia abierto.

### Hacer una captura nueva

1. En Ajustes, activa **Modo diagnóstico**.
2. Abre el portal ("Abrir portal"), pulsa **Notas** y, si hace falta, el botón **NF** de una materia.
3. Pulsa **Guardar HTML de esta página** y elige dónde guardarlo. El token antifalsificación se guarda como `REDACTED`.

### Probarla en el teléfono

En Ajustes (con el modo diagnóstico activo), **Probar el lector con un HTML guardado** muestra lo que la app entiende de la página: ciclo, materias y detalle abierto. Si la página no tiene la estructura esperada, lo dice. No cambia tus datos.

### Agregarla a los tests

1. **Anonimízala**: reemplaza tu nombre, carnet, foto (`ObtenerImagenP/<número>`), carrera, campus, CUM, avance y cualquier correo o teléfono. Deja materias, actividades, porcentajes y notas tal cual. No subas capturas sin anonimizar: el repositorio puede ser público.
2. Cópiala a `app/src/test/resources/fixtures/` y agrega o ajusta los tests en `app/src/test/java/com/example/minimo/data/portal/`.
3. Ejecuta `./gradlew testDebugUnitTest`.

Si el portal cambia y la sincronización muestra "El portal cambió", haz una captura nueva, compruébala con "Probar el lector" y actualiza el lector contra esa captura.

## Estructura

```
app/src/main/java/com/example/minimo/
  domain/        Kotlin puro: Course, Evaluation, GradeCalculator, RoundingPolicy, validación
  data/          CourseRepository y almacenamiento local (DataStore con un snapshot JSON)
  data/portal/   Sesión, URLs permitidas, lector (Jsoup) y sincronización con el portal
  ui/            Pantallas Compose y un ViewModel por pantalla
```

Stack: Kotlin, Jetpack Compose con Material 3, Navigation Compose, ViewModel y StateFlow, Coroutines, kotlinx.serialization, DataStore y Jsoup. Las versiones están en `gradle/libs.versions.toml`.
