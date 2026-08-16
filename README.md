# 📱 MediaVault - Android Downloader & AI Cloud Upscaler

**MediaVault** es una aplicación nativa para Android construida con **Kotlin**, **Jetpack Compose**, **Material 3** y arquitectura modular limpia (**MVVM + Repository + Hilt + Room + WorkManager + ExoPlayer**).

Permite descargar video y audio desde múltiples redes sociales y sitios de video (YouTube, Instagram Reels, TikTok, Twitter/X, Facebook, Reddit, Vimeo, Twitch clips, etc.) con selector de resoluciones dinámicas (144p a 8K) y extracción de audio independiente (MP3, M4A, OPUS, FLAC a 128k-320k), además de un módulo para escalado por Inteligencia Artificial en servidores GPU remotos.

---

## ✨ Características Principales

1. **Detección Automática de Plataformas**: Reconoce enlaces pegados o compartidos desde otras aplicaciones (`ACTION_SEND`).
2. **Descarga Multi-Formato**: Soporte desde 144p hasta 8K (30/60 fps) y audio independiente con carátulas y subtítulos incrustados.
3. **Escalado IA con GPU (En Prueba / Beta)**:
   - Procesamiento 100% en la nube (el teléfono nunca ejecuta modelos pesados en local).
   - Conectores para backend propio en FastAPI (Real-ESRGAN / RIFE), Fal.ai y Replicate.
4. **Almacenamiento Scoped Storage / MediaStore**:
   - Guarda automáticamente en `Movies/MediaVault` y `Music/MediaVault`.
   - Sistema de etiquetas, carpetas, favoritos y papelera temporal.
5. **Reproductores Multimedia Integrados**:
   - **Video**: ExoPlayer con Picture-in-Picture (PiP) y control de velocidad (0.25x a 2.0x).
   - **Audio**: ExoPlayer con visualizador de ondas animado (waveform), ecualizador y Sleep Timer.
6. **Seguridad y Widgets**:
   - Bloqueo por huella / biometría.
   - Widget para la pantalla de inicio con pegado rápido.

---

## 🏗️ Estructura del Proyecto

```text
MediaVault/
├── app/                  # Módulo de UI (Jetpack Compose, ViewModels, Reproductores, Widgets)
├── core-storage/         # Room DB, Preferences DataStore, Scoped Storage (MediaStore)
├── core-downloader/      # Yt-Dlp extractor, Gestor de descargas, DownloadWorker
├── core-upscale/         # Cliente API para GPUs en la nube (FastAPI, Fal.ai, Replicate)
└── server-backend/       # Servidor GPU backend en Python (FastAPI + Real-ESRGAN / RIFE)
```

---

## 🚀 Requisitos y Compilación

- **Android Studio**: Ladybug, Koala, Jellyfish o más reciente.
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 35 (Android 15)
- **Java / JDK**: Java 17 o 21 (incluido en Android Studio).

### Compilar desde consola:
```powershell
# Pruebas unitarias
.\gradlew assembleDebug
```

---

## 📄 Licencia

Distribuido bajo la Licencia MIT. Consulta `LICENSE` para más información.
