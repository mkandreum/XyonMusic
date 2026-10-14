# XyonMusic Native — réplica fiel del diseño WebView, sin sus fallos

Paquete nuevo: `com.xyonmusic.player` (no pisa la instalada `com.xyonmusic.xyon.pro`; `native` es keyword Java y no compila).
Stack: Kotlin 2.0.20 + Compose BOM + Material3 + Media3 1.4.1 + Room + DataStore + Coil.
Skills aplicadas: `compose-agent` (estado/efectos/performance), `impeccable-operate` (mismo mundo visual), `jetpack-compose-audit` (reglas).

## Diseño replicado tal cual
Tomado de `descompilado/com.xyonmusic.xyon.pro-1/01_zip/assets/index.html` (105 KB):
- Fondo `#0A0A10`, acento `#f83f47`, texto `#ffffff/#a7a7a7`, light `#F0F2F5/#1c1e21`
- BottomNav 70px + MiniPlayer 55-64px, Cover 280px, Inter 400-800, páginas: library / 4u / top / search / sub-page, mini+full player con FLIP 500ms → `AnimatedVisibility` + spring
- Liquid-glass se simplifica (ver fixes): sin `backdrop-filter:url(#liquid-glass-filter)` ni `blur(120px)`

## Fallos de la versión encapsulada y fix nativo (con rigor)
1. **WebView wrapper, no nativa** — `MainActivity.loadUrl(file:///android_asset/index.html)` + `addJavascriptInterface(Android, MetadataProvider)`. Jank por `drift 25s`, shimmer, CoverFlow JS con drag manual. → Compose `LazyColumn(key=uri)` + `LazyRow` con snap, `graphicsLayer`, Coil. Sin WebView.
2. **CDN obligatoria** — Google Fonts + `unpkg.com/phosphor-icons` → sin internet se queda sin iconos/fuente. → `FontFamily.Default` offline + Material Icons incluidos en APK.
3. **Escaneo roto** — `processDirectoryForUris` solo 1 nivel, lógica `startsWith("audio/")` invertida (metía no-audio), `clearAll+insertAll` sin metadata, JSON gigante por `evaluateJavascript("javascript:onMusicFolderSelected('...')")` (rompe con `'`). → `MediaStore.Audio` recursivo real, `READ_MEDIA_AUDIO`, orden por título. SAF queda opcional.
4. **Metadata bloqueante** — `getMetadataForSong()` con `MediaMetadataRetriever.setDataSource` en el hilo del bridge + carátula base64 JPEG85 512px por canción (OOM). → `artUri content://media/external/audio/albumart` + Coil con caché. Metadata vía MediaStore, sin base64.
5. **Audio sin fondo** — `<audio>` HTML + `MusicService` con ExoPlayer/MediaSession pero el JS nunca lo llama (broadcast `STATE_UPDATE` sin oyente). Al bloquear se corta, sin notificación ni audio-focus. → `MusicService : MediaSessionService` + `MediaController` en `PlayerViewModel`, `handleAudioBecomingNoisy`, notificación real.
6. **Manifest impublicable** — `debuggable=true`, `testOnly=true`, `supportsRtl` ok pero sin `READ_MEDIA_AUDIO`. → Manifest limpio, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `targetSdk 34`, R8 en release.
7. **Haptics frágil** — `createWaveform(timings, amplitudes)` con `-1` como amplitud (reservado). → `VibrationEffect.createPredefined(EFFECT_CLICK)` según API.
8. **Player FLIP manual** — `getBoundingClientRect()+element.animate()` 500ms → frames perdidos en WebView. → `AnimatedVisibility` declarativa.
9. **Sin paginación** — 4000+ nodos en DOM + `IntersectionObserver` casero. → Paging-ready: `key`, `collectAsStateWithLifecycle`, `remember` filtrado.
10. **Persistencia frágil** — `localStorage xyon_player_state` con `src=contentUri` que muere al perder permiso. → DataStore + MediaStore IDs estables (pendiente guardar queue).

## Abrir / compilar
1. Abrir `XyonMusic-Native` en Android Studio Koala+.
2. `Sync` + `Run app`. Pide `READ_MEDIA_AUDIO` en Android 13+.
3. Sin música local → lista vacía (correcto, antes mostraba placeholders infinitos).

## Estructura
- `MainActivity.kt` — edge-to-edge, tabs Biblioteca/Para ti/Top/Buscar, mini+full player
- `PlayerViewModel.kt` — MediaController + StateFlow + `collectAsStateWithLifecycle`
- `data/MusicRepository.kt` — MediaStore recursivo
- `playback/MusicService.kt` — ExoPlayer real
- `ui/theme/Theme.kt` — tokens `#0A0A10/#f83f47`
- `ui/components/SongRow.kt`, `ui/screens/Screens.kt`
