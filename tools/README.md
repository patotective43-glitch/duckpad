# Herramientas de prueba en el PC

Solo para desarrollo; no hacen falta para jugar. Necesitan Python 3 en Windows y la `SDL3.dll` de algún programa (por ejemplo, la de Ryujinx).

- `sdl3_probe.py <SDL3.dll> [correspondencia]`: cómo ve los mandos esa SDL3, con su GUID y si es mando.
- `sdl3_joystick.py <SDL3.dll> <segundos> [VID] [--rumble]`: el mando como joystick en bruto (índices de botones, ejes y crucetas) para escribir una correspondencia. Con VID solo abre los mandos de ese fabricante; con `--rumble` pide vibración.
- `sdl3_watch.py <SDL3.dll> <segundos> [correspondencia]`: cada cambio de botones y ejes por la API de mandos de SDL.
- `joy_probe.py [segundos]`: mandos genéricos vistos por la API clásica de Windows (winmm).

La prueba de reconexión está en [verification/reconnect.py](../verification/reconnect.py); necesita ADB y el móvil conectado por USB.
