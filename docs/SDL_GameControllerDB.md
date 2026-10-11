# Entrada para SDL_GameControllerDB (sin enviar)

Se enviará cuando pid.codes apruebe `1209:D0CC` (https://github.com/pidcodes/pidcodes.github.com/pull/1307) y sea la identidad por defecto de DuckPad.

Línea para la sección Windows de `gamecontrollerdb.txt`, comprobada con SDL 3 y la API de mandos (los 21 controles):

```
0300000009120000ccd0000000000000,DuckPad,a:b0,b:b1,back:b10,dpdown:h0.4,dpleft:h0.8,dpright:h0.2,dpup:h0.1,guide:b12,leftshoulder:b6,leftstick:b13,lefttrigger:a4,leftx:a0,lefty:a1,rightshoulder:b7,rightstick:b14,righttrigger:a5,rightx:a3,righty:a6,start:b11,x:b3,y:b4,platform:Windows,
```

Descripción para la solicitud de cambio:

> DuckPad is an open-source Android app (https://github.com/patotective43-glitch/duckpad) that turns a phone into a Bluetooth LE HID gamepad (HID over GATT), VID/PID 1209:D0CC from pid.codes. On Windows it is mounted as a generic HID game controller through DirectInput. Mapping tested on Windows 11 with SDL 3 (gamepad API): every button, the hat, both sticks and both triggers. Axes 2 and 7 are the Simulation Accelerator and Brake usages, which DirectInput places in the Y and Rz slots; the triggers are repeated as Rx/Ry on axes 4 and 5.
