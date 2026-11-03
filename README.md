# MIA Android — aplicación nativa

Este repositorio contiene la aplicación Android nativa de MIA. No es un acceso directo ni una WebView: la interfaz de chat, ajustes, perfil, selección de avatar, voz, adjuntos de imagen y almacenamiento local se implementan con vistas Android.

## IA
- Conexión con Google Gemini 2.0 Flash mediante la API de Google AI Studio.
- La clave API se introduce en Ajustes dentro de la app y se guarda localmente en el dispositivo.
- El chat y los ajustes básicos se conservan en el dispositivo.

## Funciones incluidas en esta primera versión nativa
- Chat nativo con historial local.
- Personalidades Normal, Bro, Serio, Humor, Diabólico y Salseo.
- Intensidad, nombre y equipo en el perfil.
- Selector de 12 avatares.
- Entrada por voz y lectura de respuestas en voz alta.
- Adjuntar imágenes para analizarlas con Gemini.
- Tema oscuro con detalles dorados y azules.

## Generar APK
GitHub Actions ejecuta el workflow Build MIA APK en cada cambio del proyecto Android. Descarga el artefacto MIA-Android-APK desde la ejecución completada.
