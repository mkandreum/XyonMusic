## XyonMusic 2.2

APK release optimizado con R8. **Desinstala la versión anterior antes de instalar esta**: a partir de ahora el APK se firma siempre con la misma clave, así que las próximas versiones se instalarán encima sin desinstalar.

### Spotify dentro de XyonMusic
- Las canciones de Spotify se reproducen sin salir de la app, con el SDK oficial de Spotify (App Remote). El audio lo pone la app de Spotify en segundo plano, y se controla todo desde el reproductor de XyonMusic: carátula, progreso, play/pausa, anterior/siguiente, aleatorio y repetir.
- El mini reproductor también muestra lo que suena en Spotify, con un indicador verde.
- Si no tienes la app de Spotify instalada, se usa como respaldo el control por Web API.

**Para activarlo, una sola vez:**
1. En developer.spotify.com/dashboard, abre tu app → Settings → Android packages.
2. Añade el paquete `com.xyonmusic.player` y la huella SHA-1 que aparece al final de estas notas (también la ves en Ajustes → Spotify, y se copia tocándola).
3. Ten la app de Spotify instalada y con sesión iniciada. Para elegir canciones concretas hace falta Premium.

### CoverFlow con Spotify
- Los álbumes de tus "Canciones que te gustan" aparecen en el CoverFlow vertical y horizontal, mezclados con tus álbumes locales y marcados con un corazón verde.
- Al tocar uno se abre su ficha: puedes reproducir el álbum completo o tus me gusta desde esa canción.

### Diseño
- La barra inferior y el botón de búsqueda son más translúcidos (cristal esmerilado) y conservan el borde con aberración cromática.
