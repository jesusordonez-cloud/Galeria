# Galería — visor de fotos 3D

Un único archivo, `index.html`. Ábrelo con doble clic en cualquier navegador moderno (Chrome, Edge, Safari, Firefox). No necesita servidor ni instalación; las fotos nunca salen de tu equipo.

## Uso
- Menú (…) → **Añadir fotos**, o arrastra imágenes sobre la ventana.
- Las fotos se agrupan por la fecha de captura (EXIF) y se muestran en un carrusel 3D.
- Flechas, arrastre, trackpad, teclado (← →) o tocar una tarjeta lateral para navegar. Tocar la central abre pantalla completa.
- **Tema**: Medianoche, Ámbar, Esmeralda, Rosa, Mono o Claro. Se recuerda entre sesiones.
- **Fondo de ambiente**: sube una imagen o usa la foto actual; ajusta la intensidad con el control deslizante.
- **Reproducción automática**: avanza sola cada 4 segundos.
- Favoritos se guardan en el navegador.

## Reconocimiento
- Rostros: se detectan con la API nativa del navegador (Chrome/Edge en escritorio y Android). En Safari/Firefox no está disponible.
- Escenas, descripciones y rostros con Claude ("Analizar con Claude") solo funcionan en la versión publicada en claude.ai, donde el visor concede acceso a Claude. En el archivo local esa opción aparece desactivada.

## Personalizar
Los colores de cada tema están al inicio del `<style>` en bloques `:root[data-theme="..."]`. Para añadir uno, copia un bloque, cambia los valores y añade un botón en la sección `#themes`.
