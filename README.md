# DuckPad

DuckPad convierte un móvil Android en un mando Bluetooth para Windows. El móvil se anuncia como mando HID por Bluetooth Low Energy (HID over GATT), así que en el PC no hay que instalar nada: se empareja como cualquier mando.

- Todos los controles en pantalla, recolocables en un editor: dos sticks flotantes, cruceta, A, B, X, Y, LB, RB, LT, RT, Vista, Guía, Menú, L3 y R3.
- Horizontal o vertical, cada orientación con sus propias posiciones.
- Los botones de volumen pueden hacer de gatillos o de cualquier otro botón.
- Vibración opcional al pulsar.
- Textos en español y en inglés, tema claro y oscuro.

Requisitos: Android 9 o posterior con Bluetooth LE capaz de anunciarse como periférico. Probado en un Realme 7 5G (Android 12) con Windows 11.

[English summary below](#english).

## Cómo se usa

1. Abre DuckPad, da permiso de Bluetooth y toca **Iniciar sesión**.
2. En el PC, pulsa **Conectar** en el aviso de «DuckPad». Si no sale, ve a Configuración > Bluetooth y dispositivos > Agregar dispositivo > Bluetooth y elige el **DuckPad del icono de dos dispositivos**, no el del teléfono.
3. Cuando el mando diga **Conectado**, a jugar. Las siguientes veces basta con tocar Iniciar sesión: Windows se conecta solo.
4. Para terminar, pulsa atrás dos veces o **Terminar sesión**.

No cierres la app a la fuerza con el PC conectado: si el proceso muere con el enlace abierto, Windows pierde el mando y hay que quitarlo y emparejarlo de nuevo.

## Cómo lo ve Windows

DuckPad se presenta con su propia identidad y Windows lo monta como **mando HID genérico** (DirectInput):

- Lo usan los programas y juegos que leen mandos genéricos con SDL o DirectInput. Con SDL hace falta la correspondencia de botones de abajo hasta que esté en SDL_GameControllerDB.
- Los juegos que solo aceptan mandos XInput no lo ven directamente. Steam puede traducirlo con **Steam Input para mandos genéricos** (Steam > Parámetros > Mando > Configuración avanzada).
- La vibración que piden los juegos no llega: el HID genérico de Windows solo hace vibrar mandos con force feedback.

## Identidad del mando

Windows reconoce el mando por el PnP ID del servicio Device Information: VID, PID y versión.

- Por defecto, `1209:0001:0100`. Es un PID de pruebas de [pid.codes](https://pid.codes/1209/0001/), compartido y no apto para versiones distribuidas.
- Se ha pedido a pid.codes un PID propio, `1209:D0CC`. Cuando lo aprueben pasará a ser la identidad por defecto.
- En **Opciones avanzadas**, al pie de la pantalla principal, se pueden guardar dos identidades (A y B) y elegir cuál se usa. Windows guarda la identidad del primer emparejamiento: después de cambiarla hay que quitar DuckPad en Windows y emparejarlo de nuevo.

## Correspondencia para SDL

Mientras DuckPad no esté en SDL_GameControllerDB, un programa con SDL2 o SDL3 lo reconoce como mando si se define `SDL_GAMECONTROLLERCONFIG` con esta línea antes de abrirlo:

```
03000000091200000100000000000000,DuckPad,a:b0,b:b1,x:b3,y:b4,back:b10,guide:b12,start:b11,leftstick:b13,rightstick:b14,leftshoulder:b6,rightshoulder:b7,dpup:h0.1,dpdown:h0.4,dpleft:h0.8,dpright:h0.2,leftx:a0,lefty:a1,rightx:a3,righty:a6,lefttrigger:a4,righttrigger:a5,platform:Windows,
```

Es para la identidad `1209:0001`. Con otra, cambian los bytes del VID y el PID en el GUID (posiciones 9 a 12 y 17 a 20, en little-endian). Detalles y pruebas en [docs/PRUEBAS.md](docs/PRUEBAS.md).

## Compilar

Desde `android`, con Java 17 y el SDK de Android (API 36) en `ANDROID_HOME`:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

Versión de publicación (APK y AAB firmados, reducidos con R8):

```powershell
$env:DUCKPAD_SIGNING = "C:\ruta\a\keystore.properties"
.\gradlew.bat assembleRelease bundleRelease
```

`keystore.properties` lleva `storeFile`, `storePassword`, `keyAlias` y `keyPassword`, y nunca va al repositorio. Sin él, la versión de publicación se compila sin firmar. La variante `staging` es igual que la de publicación pero firmada con la clave de depuración, para probar el código reducido encima de una instalación de pruebas.

Herramientas de prueba para el PC en [tools](tools/README.md).

## Privacidad

DuckPad no recoge ni envía datos y no tiene permiso de Internet. Ver [PRIVACY.md](PRIVACY.md).

## Licencia y créditos

GPL-3.0. Ver [LICENSE](LICENSE). El descriptor HID se basa en [ESP32-BLE-CompositeHID](https://github.com/Mystfit/ESP32-BLE-CompositeHID) (MIT); su aviso está en [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Nota legal

La identidad personalizada de Opciones avanzadas es responsabilidad de quien la configura. El VID `045E` pertenece a Microsoft, y usar el VID o el PID de otro fabricante puede vulnerar sus marcas o sus condiciones de uso. DuckPad no incluye ningún preajuste para imitar mandos de otros fabricantes.

<a id="english"></a>
## English

DuckPad turns an Android phone into a Bluetooth LE HID gamepad (HID over GATT) for Windows, with no software on the PC. Windows mounts it as a generic HID game controller (DirectInput), not XInput. SDL programs need the mapping above until DuckPad is in SDL_GameControllerDB; Steam can translate it with Steam Input for generic controllers. The default PnP ID is the pid.codes test PID `1209:0001`; `1209:D0CC` has been requested. Two custom identities can be stored in Advanced options; changing the identity requires removing the controller in Windows and pairing again. Licensed under GPL-3.0. DuckPad collects no data and has no Internet permission.
