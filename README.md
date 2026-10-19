# TV EN VIVO · Hisense VIDAA

Aplicación Web TV diseñada para televisores Hisense con **VIDAA OS**, inspirada en la interfaz de referencia acordada: navegación lateral, recomendaciones, categorías y guía de programación.

## Tecnología
VIDAA ejecuta aplicaciones web HTML5/CSS/JavaScript. Este proyecto no genera APK Android.

## Publicación
El repositorio incluye un workflow de GitHub Pages. Una vez habilitado **Settings → Pages → Source: GitHub Actions**, la URL prevista es:

`https://jesusordonez-cloud.github.io/Galeria/`

## Instalación de prueba en Hisense VIDAA
1. Abrir el navegador de la TV.
2. Escribir `hisense://debug`.
3. En la plataforma de depuración capturar:
   - App Name: `TV EN VIVO`
   - App URL: la URL HTTPS publicada
   - Resolution: `1080`
   - Thumbnail/Icon: URL HTTPS de `icon.svg` (opcional)
4. Seleccionar **INSTALL**.
5. Salir con **Exit**, abrir **Apps** y localizar **TV EN VIVO**.

## Control remoto
La interfaz soporta flechas direccionales y Enter mediante navegación por foco.

> Nota: la interfaz está preparada para conectar fuentes HLS/streaming legales y EPG. Los canales comerciales que requieran autenticación, DRM o derechos territoriales no deben agregarse mediante enlaces no autorizados.
