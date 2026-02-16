# Pedidos Sorrentinos (Android)

Proyecto Android en **Kotlin + Jetpack Compose + Room (SQLite)**, pensado para uso **offline**.

## Funcionalidades incluidas

- Gestión de pedidos con estados: `PENDIENTE`, `ENTREGADO`, `CANCELADO`.
- Ajustes persistidos en Room como singleton (`id=1`):
  - `costoDefaultPorDocena`
  - `ventaDefaultPorDocena`
- Al crear pedido se toma snapshot de costo/venta unitarios desde Ajustes.
- Cambios posteriores en Ajustes no alteran pedidos existentes.
- Resumen semanal con:
  - total ventas
  - total costos
  - ganancia
  - cantidad de pedidos
  - cantidad de pendientes
- Navegación inferior con 4 tabs:
  - Pendientes
  - Entregados
  - Resumen
  - Ajustes

## Cómo abrir en Android Studio

1. Abrí Android Studio.
2. Elegí **Open**.
3. Seleccioná la carpeta raíz del proyecto (`app-s`).
4. Esperá sincronización de Gradle.

## Correr en emulador o dispositivo

1. Conectá un dispositivo Android con depuración USB, o iniciá un emulador.
2. En Android Studio, elegí el dispositivo destino.
3. Presioná **Run ▶** sobre la configuración `app`.

## Generar APK debug

1. Ir a menú: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. Esperar la compilación.
3. Android Studio mostrará un enlace para abrir la carpeta de salida.
4. El APK debug suele quedar en:
   - `app/build/outputs/apk/debug/app-debug.apk`
