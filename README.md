# XyonMusic — app nativa Android (Compose + Media3)

Réplica fiel del diseño de la versión WebView (`com.xyonmusic.xyon.pro`), reescrita en nativo con rigor y sin sus fallos. Paquete nuevo **`com.xyonmusic.player`** (no pisa la instalada).

- Kotlin 2.0.20 · Compose BOM · Material3 · Media3 1.4.1 · Room · DataStore · Coil
- Biblioteca por `MediaStore` (recursiva, offline), CoverFlow nativo con `LazyRow` + `graphicsLayer`, mini + full player, Para ti / Top / Buscar, claro + oscuro, fondo real con `MediaSessionService`.

## Compilar

```bash
./gradlew :app:assembleDebug      # debug
./gradlew :app:assembleRelease    # release (firmado si hay keystore.properties)
```

SDK 35, Java 17. Ver `FALLOS_Y_FIXES.md` para el audit WebView → fix nativo (10 puntos).

## Firmar tu propio release

```bash
keytool -genkeypair -keystore release.keystore -alias xyon -keyalg RSA -keysize 2048 -validity 9125
```

y crea `keystore.properties`:

```properties
storeFile=release.keystore
storePassword=...
keyAlias=xyon
keyPassword=...
```
