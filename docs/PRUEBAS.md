# Pruebas y decisiones técnicas

Pruebas del 11 de octubre de 2026 con un Realme 7 5G (Android 12) y un portátil con Windows 11. Lo que no aparece aquí no se ha probado.

## Identidad genérica (`1209:0001:0100`)

- **Windows** crea `HID\{00001812-…}_DEV_VID&021209_PID&0001_REV&0100_…` como «Dispositivo de juego compatible con HID». Lee el PnP ID (característica `2A50`) durante el emparejamiento.
- **SDL3** (la `SDL3.dll` de Ryujinx canary 1.3.272) ve un joystick «DuckPad», GUID `0300e539091200000100000000000000`, 8 ejes, 16 botones y 1 cruceta. Sin correspondencia no es un mando (`SDL_IsGamepad` falso). Con la correspondencia del README, los 21 controles llegan a su sitio por la API de mandos: 11 botones, 4 direcciones de cruceta, 2 gatillos de 0 a 32767 y 2 ejes por stick, de punta a punta.
- **API clásica (winmm)**: 15 botones y 6 ejes. Sticks en X/Y y Z/R; LT llega por V y RT por U.
- **Vibración**: no funciona. `SDL_RumbleJoystick` responde «That operation is not supported» y Windows no escribe ningún informe de salida.
- **Reconexión**: tres ciclos de cierre con atrás dos veces, proceso terminado ya sin enlace, app abierta en frío e Iniciar sesión. Windows reconectó solo en los tres, entre 0,87 y 2,17 s después de empezar el anuncio. Resultados en [verification/reconnect-results.json](../verification/reconnect-results.json).

## Informe HID de 18 bytes

Los 16 primeros bytes siguen la disposición del mando de ESP32-BLE-CompositeHID: sticks en X/Y y Z/Rz, gatillos como Acelerador y Freno de la página Simulation, cruceta y 15 botones. DirectInput coloca el Acelerador y el Freno en los huecos de Y y Rz: los gatillos no llegaban a ninguna parte y aparecían dos ejes que repetían el Y de cada stick (los ejes 2 y 7 de SDL). Por eso el informe añade al final los dos gatillos otra vez, como Rx y Ry de 8 bits.

## Estado «Conectado»

«Conectado» significa que el PC está enlazado, tiene activadas las notificaciones del mando y acepta los informes de entrada (entregas en los últimos 3,5 s). No espera ningún informe de salida: algunos drivers lo envían al montar el mando o al pedir vibración, pero no al reconectar.

## Varios aparatos vinculados

El móvil puede estar vinculado con más de un aparato que se conecte como host de mandos. En las pruebas, otro aparato vinculado se conectaba a la vez que el PC; los dos se quitaban el puesto y el mando se conectaba y desconectaba en bucle. Desde entonces:

- el PC que usa el mando lo conserva aunque se conecte otro aparato;
- el último PC que empareja el mando durante una sesión queda como preferido y recupera el mando en cuanto usa el servicio HID;
- se ignoran los aparatos que Android identifica como audio, reloj, teléfono, salud, juguete o imagen;
- al terminar la sesión se corta a todos los aparatos que usaban el mando y los servicios no se retiran hasta que se han ido todos. Retirarlos con un PC enlazado hace que Windows pierda el mando.

Sin probar: que el PC preferido recupere el mando cuando el otro aparato llega antes.

## Otras identidades

La identidad se cambia en Opciones avanzadas (dos guardadas, A y B). Una identidad de otro fabricante que SDL o Steam reconocen por VID y PID hace que traten el mando con el protocolo de ese fabricante, y no entienden el informe de DuckPad: SDL lo lista como mando pero no recibe ninguna pulsación. Las identidades de otros fabricantes solo sirven si el informe sigue su mismo formato.

## Accesibilidad

El mando dibujado expone cada control a TalkBack como un elemento con nombre y posición: 13 botones, 4 direcciones de la cruceta y los 2 sticks, con instrucciones para moverlos. En el mando, los botones y la cruceta se pulsan con doble toque. Comprobado con `uiautomator dump` en el editor de controles; sin probar con TalkBack activado.

## Versión de publicación

- API objetivo 36. Desde Android 13 el botón atrás llega por `OnBackInvokedCallback`, porque con API 36 Android ya no llama a `onBackPressed`. Sin probar en Android 13 o posterior: el móvil de pruebas tiene Android 12.
- R8 y reducción de recursos activados. La variante `staging` (mismo código reducido, clave de depuración) se instaló y se recorrió la pantalla principal, las dos identidades y el editor de controles sin errores. No se abrió una sesión con ella.
- Lint: 0 avisos.

## PID propio (`1209:D0CC`, pedido a pid.codes)

Probado con la identidad B en `1209:D0CC:0100`:

- Windows crea `HID\{00001812-…}_DEV_VID&021209_PID&D0CC_REV&0100_…` como mando HID genérico.
- SDL3 le da el GUID `0300e53909120000ccd0000000000000`. Con la línea para SDL_GameControllerDB de [SDL_GameControllerDB.md](SDL_GameControllerDB.md), que lleva el GUID sin CRC (`0300000009120000ccd0000000000000`), los 21 controles llegan a su sitio por la API de mandos.
- **Ryujinx** (canary 1.3.272), abierto con esa línea en `SDL_GAMECONTROLLERCONFIG`: lista DuckPad como dispositivo de entrada y responde a sticks y gatillos.
- **Steam**, con «Activar Steam Input para mandos genéricos»: detecta DuckPad (`type: 1209 d0cc` en su registro de mandos), se configura con «Definir disposición» y la prueba de entradas responde.

## Sin probar

- Un juego que solo acepta mandos Xbox, abierto desde Steam con Steam Input.
- Reconexión tras días o con el PC suspendido, otros móviles y otros PCs.
