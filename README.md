# YT Descargador

App Android sencilla: pegas un link de YouTube, eliges la calidad y el video se guarda en tu teléfono.

- Usa **yt-dlp** (descarga) y **FFmpeg** (une video + audio para tener buena calidad).
- Calidades: mejor disponible (hasta 4K), 1080p, 720p, 480p o solo audio MP3.
- También funciona con el botón **Compartir** de la app de YouTube.
- Los videos quedan en `Películas/YTDescargador` y los audios en `Música/YTDescargador`.
- Se actualiza yt-dlp solo al abrir la app (con internet), porque YouTube cambia seguido.

> Úsala solo para uso personal y con contenido que tengas permiso de descargar.

---

## Cómo obtener el APK (sin instalar nada en tu computador)

Usa GitHub, que compila la app gratis en la nube.

1. Crea una cuenta gratis en https://github.com
2. Crea un repositorio nuevo (botón **New**), ponle el nombre `YTDescargador` y déjalo **Private**.
3. En el repositorio, elige **uploading an existing file** y arrastra **todo el contenido** de esta carpeta
   (incluida la carpeta oculta `.github`). Si no la ves, activa "mostrar archivos ocultos" en tu computador.
   Presiona **Commit changes**.
4. Ve a la pestaña **Actions**. Verás una tarea llamada **Compilar APK** corriendo (tarda unos 5-10 minutos).
   Si no arranca sola, entra a "Compilar APK" y presiona **Run workflow**.
5. Cuando termine con una palomita verde, entra a esa ejecución y baja hasta **Artifacts**.
   Descarga **YTDescargador-apk** (es un .zip; dentro está el `.apk`).
6. Pasa el `.apk` a tu celular, ábrelo y acepta **instalar de fuentes desconocidas** cuando te lo pida.

## Alternativa: Android Studio

1. Instala Android Studio y elige **Open** sobre esta carpeta.
2. Espera a que sincronice y presiona **Build → Build APK(s)**.

## Si algo deja de funcionar

- Abre la app con internet unos segundos para que se actualice yt-dlp y vuelve a intentar.
- Si el link es de un video privado, con restricción de edad o de una transmisión en vivo, puede fallar.
