# TV en Vivo

Aplicación Android para reproducir televisión gratuita disponible públicamente por Internet.

## Funciones

- Reproductor nativo basado en Android Media3 / ExoPlayer.
- Catálogo remoto de **México**, canales en **español** y catálogo **mundial**.
- Listas actualizadas desde IPTV-org, sin necesidad de recompilar la APK.
- Búsqueda por nombre y categoría.
- Filtro por categorías.
- Favoritos persistentes.
- Logos de canales.
- Guía EPG con programa **Ahora** y **Siguiente** cuando existe información disponible.
- Soporte para HLS y DASH.
- Encabezados Referer/User-Agent de listas M3U cuando están publicados.
- Picture-in-Picture en Android 8 o superior.
- Exclusión de categorías marcadas para adultos.

## Fuentes

La aplicación no aloja señales de televisión.

Directorio de streams:
- https://iptv-org.github.io/iptv/countries/mx.m3u
- https://iptv-org.github.io/iptv/languages/spa.m3u
- https://iptv-org.github.io/iptv/index.m3u

Índice EPG:
- https://iptv-org.github.io/api/guides.json

La disponibilidad de un canal puede cambiar, estar limitada geográficamente o depender del proveedor de la señal. La aplicación no intenta eludir DRM, autenticación, suscripciones ni restricciones de pago.

## Android

- applicationId: `com.jesusordonez.galeria`
- Nombre visible: **TV en Vivo**
- minSdk: 24
- targetSdk / compileSdk: 36
- Media3: 1.11.1
- Java: 17

El `applicationId` se conserva respecto a la aplicación anterior del repositorio para mantener continuidad técnica. Si Android rechaza una actualización por firma distinta, desinstala la APK anterior e instala la nueva.

## Compilar

GitHub Actions compila automáticamente cada cambio en `main`.

También puede compilarse localmente:

```bash
gradle assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```
