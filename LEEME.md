# Vigía para Android (tabletas de la empresa)

App que abre la misma página de Vigía (Netlify) y agrega lo que el navegador no puede:
alarmas que suenan con la pantalla apagada o con Zello abierto, voz nativa, vibración,
pantalla siempre encendida y arranque automático al prender la tableta.

- Cada cambio a `main` compila el APK solo (pestaña **Actions**) y lo publica en **Releases**.
- La llave de firma está en `firma/vigia.jks.b64` (repositorio privado). No la borres ni la cambies:
  sin ella, las tabletas no aceptan la actualización y habría que desinstalar.
