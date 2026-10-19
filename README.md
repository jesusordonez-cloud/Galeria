# Galería

Aplicación Android basada en el visor de fotos 3D incluido en `galeria.zip`.

## Qué incluye

- Carrusel 3D de fotografías.
- Selección múltiple de imágenes desde Android.
- Agrupación por fecha de captura EXIF cuando está disponible.
- Temas visuales: Medianoche, Ámbar, Esmeralda, Rosa, Mono y Claro.
- Favoritos persistentes en el dispositivo.
- Fondo ambiental configurable.
- Búsqueda, filtros, pantalla completa, navegación táctil y reproducción automática.
- Demo integrada sin necesidad de cargar fotografías.
- Versión web utilizable directamente desde `index.html`.

> La función original “Analizar con Claude” dependía de APIs especiales de claude.ai, por lo que no se incluye en la versión Android independiente.

## Proyecto Android

- `applicationId`: `com.jesusordonez.galeria`
- `minSdk`: 24
- `targetSdk`: 36
- `compileSdk`: 36
- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Java: 17

La interfaz web se mantiene en un único `index.html` en la raíz. Antes de compilar, Gradle lo copia automáticamente a `app/src/main/assets/index.html`.

## Generar APK

El workflow `.github/workflows/android-build.yml` compila automáticamente una APK de depuración cuando hay cambios en `main` o cuando se ejecuta manualmente desde **Actions → Build Android APK → Run workflow**.

Al terminar, la APK queda disponible como artefacto con el nombre `Galeria-debug-apk`.

## Compilación local

Con Android SDK 36, Java 17 y Gradle 8.13 instalados:

```bash
gradle assembleDebug
```

Resultado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Base original

El archivo `LEEME.md` conserva las instrucciones del visor web incluido en el paquete original.
