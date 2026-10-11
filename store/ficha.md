# Ficha de Google Play (borrador)

## Español

**Nombre** (máx. 30): DuckPad: mando para PC

**Descripción breve** (máx. 80): Usa el móvil como mando Bluetooth para Windows, sin instalar nada en el PC.

**Descripción completa:**

DuckPad convierte tu móvil en un mando Bluetooth para tu ordenador con Windows. El PC lo empareja como cualquier otro mando: no hay que instalar programas ni drivers.

Qué incluye:
- Dos sticks flotantes: apoya el pulgar donde quieras y el stick aparece ahí.
- Cruceta, A, B, X, Y, LB, RB, LT, RT, Vista, Guía, Menú, L3 y R3.
- Editor para colocar cada control donde te resulte cómodo, en horizontal y en vertical.
- Los botones de volumen pueden hacer de gatillos o de cualquier otro botón.
- Vibración al pulsar, opcional.
- Reconexión automática: la próxima vez basta con tocar Iniciar sesión.

Compatibilidad: Windows lo reconoce como mando genérico (HID). Funciona en programas y juegos que aceptan mandos genéricos, y en Steam con Steam Input para mandos genéricos. Los juegos que solo admiten mandos de Xbox fuera de Steam no lo ven. La vibración que piden los juegos no llega al móvil.

Necesita Bluetooth LE en el móvil y en el PC. Código abierto (GPL-3.0). Sin anuncios ni recogida de datos.

## English

**Name** (max 30): DuckPad: gamepad for PC

**Short description** (max 80): Use your phone as a Bluetooth controller for Windows, no PC software needed.

**Full description:**

DuckPad turns your phone into a Bluetooth controller for your Windows PC. The PC pairs it like any other controller: no programs or drivers to install.

What you get:
- Two floating sticks: rest your thumb anywhere and the stick appears there.
- D-pad, A, B, X, Y, LB, RB, LT, RT, View, Guide, Menu, L3 and R3.
- An editor to place every control where it feels comfortable, in landscape and portrait.
- Volume buttons can work as triggers or any other button.
- Optional vibration on press.
- Automatic reconnection: next time, just tap Start session.

Compatibility: Windows recognizes it as a generic (HID) controller. It works in programs and games that accept generic controllers, and in Steam with Steam Input for generic controllers. Games that only support Xbox controllers outside Steam won't see it. Rumble requested by games doesn't reach the phone.

Requires Bluetooth LE on the phone and the PC. Open source (GPL-3.0). No ads, no data collection.

## Formularios de Play Console

- **Categoría**: Herramientas.
- **Seguridad de los datos**: no recoge ni comparte datos. Sin permiso de Internet.
- **Política de privacidad**: https://github.com/patotective43-glitch/duckpad/blob/main/PRIVACY.md
- **Servicio en primer plano** (`connectedDevice`): mantiene el enlace Bluetooth con el PC mientras la sesión de mando está abierta; el usuario la inicia y la termina, y se muestra una notificación con «Terminar sesión». Play pide un vídeo corto que lo muestre.
- **Permisos de Bluetooth** (`BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`): anunciarse como mando y aceptar la conexión del PC.
- **Clasificación de contenido**: sin contenido sensible.
- **Público objetivo**: mayores de 13 años (la app no se dirige a niños).

## Pendiente antes de enviar

- PID propio aprobado en pid.codes y puesto como identidad por defecto (hoy es el de pruebas `1209:0001`, que no se puede distribuir).
- Capturas, icono y cabeceras: hechos, en [../playstore](../playstore/LEEME.md).
