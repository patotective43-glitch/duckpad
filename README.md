# DuckPad

DuckPad turns an Android phone or tablet into a Bluetooth controller for Windows. The phone advertises itself as a HID gamepad over Bluetooth Low Energy (HID over GATT), so the PC needs no software: you pair it like any other controller.

- Every control is on screen and can be moved and resized in an editor: two floating sticks, D-pad, A, B, X, Y, LB, RB, LT, RT, View, Guide, Menu, L3 and R3.
- Landscape or portrait, each with its own layout.
- Stick dead zone and an optional finer response near the center.
- The volume buttons can work as triggers or as any other button.
- Optional vibration on press.
- English and Spanish; follows the system theme, with a button to switch between light and dark.

Requirements: Android 9 or later with Bluetooth LE that can advertise as a peripheral. Tested on a Realme 7 5G (Android 12) and a Redmi Pad SE (Android 15) with Windows 11.

[Versión en español más abajo](#español).

## How to use it

1. Open DuckPad, allow Bluetooth and tap **Start session**.
2. On the PC, click **Connect** in the DuckPad notification. If it doesn't show up, go to Settings > Bluetooth & devices > Add device > Bluetooth and pick the **DuckPad with the two-device icon**, not the one with the phone icon.
3. When the controller says **Connected**, play. Next time, just tap Start session: Windows connects by itself.
4. To finish, press back twice or tap **End session**.

Don't force-close the app while the PC is connected: if the process dies with the link open, Windows loses the controller and you have to remove it and pair again.

## Several phones on one PC

Each phone shows up as a separate controller. In Windows they are named `DuckPad` plus the device name (for example, `DuckPad Redmi Pad SE`), so you can tell them apart in Bluetooth settings. The name is set automatically; Windows picks it up the next time the controller connects, without pairing again.

## How Windows sees it

DuckPad uses its own identity, and Windows mounts it as a **generic HID game controller** (DirectInput):

- Programs and games that read generic controllers through SDL or DirectInput can use it. SDL needs the mapping below until DuckPad is in SDL's controller database. Emulators such as Ryujinx only list controllers that SDL knows, so without the mapping they don't offer DuckPad.
- Games that only accept XInput (Xbox) controllers don't see it directly. Steam can translate it with **Steam Input for generic controllers** (Steam > Settings > Controller > Advanced settings), including for non-Steam games added to the library.
- Rumble requested by games doesn't reach the phone: Windows only sends it to generic controllers with force feedback.

## Controller identity

Windows recognizes the controller by the PnP ID in the Device Information service: VID, PID and version.

- The default is `1209:0001:0100`, a shared [pid.codes](https://pid.codes/1209/0001/) test PID that isn't meant for distributed releases.
- DuckPad's own PID, `1209:D0CC`, has been requested from pid.codes ([request](https://github.com/pidcodes/pidcodes.github.com/pull/1307)). Once approved it becomes the default.
- Under **Advanced options**, at the bottom of the main screen, you can store two identities (A and B) and choose which one to use. Windows keeps the identity from the first pairing: after changing it, remove DuckPad in Windows and pair again.

## SDL mapping

Until DuckPad is in SDL's database, a program built on SDL2 or SDL3 (Ryujinx included) treats it as a controller if the `SDL_GAMECONTROLLERCONFIG` environment variable holds the mapping when the program starts. To set it once for your Windows user, with lines for both DuckPad identities, run this in PowerShell and reopen the program:

```powershell
[Environment]::SetEnvironmentVariable('SDL_GAMECONTROLLERCONFIG', "03000000091200000100000000000000,DuckPad,a:b0,b:b1,x:b3,y:b4,back:b10,guide:b12,start:b11,leftstick:b13,rightstick:b14,leftshoulder:b6,rightshoulder:b7,dpup:h0.1,dpdown:h0.4,dpleft:h0.8,dpright:h0.2,leftx:a0,lefty:a1,rightx:a3,righty:a6,lefttrigger:a4,righttrigger:a5,platform:Windows,`n0300000009120000ccd0000000000000,DuckPad,a:b0,b:b1,x:b3,y:b4,back:b10,guide:b12,start:b11,leftstick:b13,rightstick:b14,leftshoulder:b6,rightshoulder:b7,dpup:h0.1,dpdown:h0.4,dpleft:h0.8,dpright:h0.2,leftx:a0,lefty:a1,rightx:a3,righty:a6,lefttrigger:a4,righttrigger:a5,platform:Windows,", 'User')
```

This replaces any value the variable already had. With a custom identity, the VID and PID bytes in the GUID change (characters 9 to 12 and 17 to 20, little-endian). Details and test results are in [docs/PRUEBAS.md](docs/PRUEBAS.md) (Spanish); the entry prepared for SDL_GameControllerDB is in [docs/SDL_GameControllerDB.md](docs/SDL_GameControllerDB.md).

## Building

From `android`, with Java 17 and the Android SDK (API 36) in `ANDROID_HOME`:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

Release build (signed APK and AAB, shrunk with R8):

```powershell
$env:DUCKPAD_SIGNING = "C:\path\to\keystore.properties"
.\gradlew.bat assembleRelease bundleRelease
```

`keystore.properties` holds `storeFile`, `storePassword`, `keyAlias` and `keyPassword` and never goes into the repository. Without it, the release build is unsigned. The `staging` variant matches the release build but is signed with the debug key, to test the shrunk code over a debug install.

Debug builds have a demo mode for store screenshots: `adb shell am start --activity-single-top -n dev.patotective.duckpad/.MainActivity --ez demo true` switches the app to English and shows "PC" instead of the computer's name; `--ez demo false` turns it off. Release builds ignore it.

PC test tools are in [tools](tools/README.md); store graphics are in [playstore](playstore/README.md).

## Privacy

DuckPad collects and sends no data and has no Internet permission. See [PRIVACY.md](PRIVACY.md).

## License and credits

GPL-3.0. See [LICENSE](LICENSE). The HID descriptor is based on [ESP32-BLE-CompositeHID](https://github.com/Mystfit/ESP32-BLE-CompositeHID) (MIT); its notice is in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Legal note

A custom identity set in Advanced options is the responsibility of whoever sets it. VID `045E` belongs to Microsoft, and using another manufacturer's VID or PID may infringe their trademarks or terms of use. DuckPad includes no preset that imitates other manufacturers' controllers.

<a id="español"></a>
## Español

DuckPad convierte un móvil o una tablet Android en un mando Bluetooth para Windows. Se anuncia como mando HID por Bluetooth Low Energy (HID over GATT), así que en el PC no hay que instalar nada: se empareja como cualquier mando.

- Todos los controles en pantalla, que se pueden mover y cambiar de tamaño en un editor.
- Horizontal o vertical, cada orientación con su distribución.
- Zona muerta de los sticks y respuesta opcional más precisa cerca del centro.
- Los botones de volumen pueden hacer de gatillos o de cualquier otro botón.
- Inglés y español, tema claro y oscuro.

**Uso:** toca **Iniciar sesión**; en el PC, pulsa **Conectar** en el aviso de DuckPad o agrégalo desde Configuración > Bluetooth y dispositivos y elige el DuckPad del icono de dos dispositivos. Para terminar, atrás dos veces o **Terminar sesión**. No cierres la app a la fuerza con el PC conectado.

**Varios móviles en un PC:** cada uno sale como un mando aparte, con el nombre `DuckPad` y el del aparato. Se pone solo, y Windows lo recoge la próxima vez que se conecta el mando, sin volver a emparejar.

**Compatibilidad:** Windows lo monta como mando HID genérico (DirectInput). Los programas con SDL, como Ryujinx, necesitan la correspondencia de [SDL mapping](#sdl-mapping) hasta que DuckPad esté en la base de datos de SDL; el comando de PowerShell de esa sección la deja puesta para tu usuario. Los juegos que solo aceptan mandos de Xbox lo ven a través de Steam Input para mandos genéricos. La vibración que piden los juegos no llega al móvil.

**Identidad:** por defecto `1209:0001`, el PID de pruebas de pid.codes; se ha pedido `1209:D0CC`. En Opciones avanzadas se guardan dos identidades; al cambiarla hay que quitar DuckPad en Windows y emparejarlo de nuevo. Usar el VID o el PID de otro fabricante es responsabilidad de quien lo configura.

Licencia GPL-3.0. DuckPad no recoge datos y no tiene permiso de Internet.
