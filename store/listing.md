# Google Play listing (draft)

Default language: English (United States). Spanish (es-ES) is added as a translation. Graphics are in [../playstore](../playstore/README.md).

## English

**Name** (max 30): DuckPad: gamepad for PC

**Short description** (max 80): Use your phone as a Bluetooth controller for Windows, no PC software needed.

**Full description:**

DuckPad turns your phone or tablet into a Bluetooth controller for your Windows PC. The PC pairs it like any other controller: no programs or drivers to install.

What you get:
- Two floating sticks: rest your thumb anywhere and the stick appears there.
- D-pad, A, B, X, Y, LB, RB, LT, RT, View, Guide, Menu, L3 and R3.
- An editor to move and resize every control, in landscape and portrait.
- Adjustable stick dead zone and an optional finer response near the center.
- Volume buttons can work as triggers or any other button.
- Light and dark theme, optional vibration on press.
- Several phones on one PC, each with its own name in Windows.
- Automatic reconnection: next time, just tap Start session.

Compatibility: Windows recognizes it as a generic (HID) controller. It works in programs and games that accept generic controllers, and in Steam with Steam Input for generic controllers. Games that only support Xbox controllers outside Steam won't see it. Emulators built on SDL, such as Ryujinx, need a one-time setting described on the project page. Rumble requested by games doesn't reach the phone.

Requires Bluetooth LE on the phone and the PC. Open source (GPL-3.0). No ads, no data collection.

## Español

**Nombre** (máx. 30): DuckPad: mando para PC

**Descripción breve** (máx. 80): Usa el móvil como mando Bluetooth para Windows, sin instalar nada en el PC.

**Descripción completa:**

DuckPad convierte tu móvil o tu tablet en un mando Bluetooth para tu ordenador con Windows. El PC lo empareja como cualquier otro mando: no hay que instalar programas ni drivers.

Qué incluye:
- Dos sticks flotantes: apoya el pulgar donde quieras y el stick aparece ahí.
- Cruceta, A, B, X, Y, LB, RB, LT, RT, Vista, Guía, Menú, L3 y R3.
- Editor para mover cada control y cambiar su tamaño, en horizontal y en vertical.
- Zona muerta de los sticks ajustable y respuesta opcional más precisa cerca del centro.
- Los botones de volumen pueden hacer de gatillos o de cualquier otro botón.
- Tema claro y oscuro, vibración opcional al pulsar.
- Varios móviles en el mismo PC, cada uno con su nombre en Windows.
- Reconexión automática: la próxima vez basta con tocar Iniciar sesión.

Compatibilidad: Windows lo reconoce como mando genérico (HID). Funciona en programas y juegos que aceptan mandos genéricos, y en Steam con Steam Input para mandos genéricos. Los juegos que solo admiten mandos de Xbox fuera de Steam no lo ven. Los emuladores con SDL, como Ryujinx, necesitan un ajuste que se hace una vez y está explicado en la página del proyecto. La vibración que piden los juegos no llega al móvil.

Necesita Bluetooth LE en el móvil y en el PC. Código abierto (GPL-3.0). Sin anuncios ni recogida de datos.

## Play Console forms

- **Category**: Tools.
- **Data safety**: collects and shares no data. No Internet permission.
- **Privacy policy**: https://github.com/patotective43-glitch/duckpad/blob/main/PRIVACY.md
- **Foreground service** (`connectedDevice`): keeps the Bluetooth link with the PC while the controller session is open; the user starts and ends it, and a notification offers End session. Play asks for a short video showing it.
- **Bluetooth permissions** (`BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`): advertise as a controller and accept the PC's connection. During a session the phone's Bluetooth name becomes "DuckPad" plus the device name, and the original name is restored afterwards.
- **Content rating**: no sensitive content.
- **Target audience**: 13 and over (the app isn't aimed at children).

## Before submitting

- Own PID approved by pid.codes and set as the default identity (today it is the test PID `1209:0001`, which can't be distributed).
