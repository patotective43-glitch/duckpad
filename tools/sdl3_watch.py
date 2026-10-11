"""Abre los mandos con la SDL3 de Ryujinx y muestra cada cambio de botones y ejes durante N segundos."""
import ctypes, os, sys, time

dll_path, seconds = sys.argv[1], float(sys.argv[2])
if len(sys.argv) > 3:
    os.environ["SDL_GAMECONTROLLERCONFIG"] = sys.argv[3]
sdl = ctypes.CDLL(dll_path)
sdl.SDL_Init.restype = ctypes.c_bool
sdl.SDL_GetGamepads.restype = ctypes.POINTER(ctypes.c_uint32)
sdl.SDL_OpenGamepad.restype = ctypes.c_void_p
sdl.SDL_OpenGamepad.argtypes = [ctypes.c_uint32]
sdl.SDL_GetGamepadName.restype = ctypes.c_char_p
sdl.SDL_GetGamepadName.argtypes = [ctypes.c_void_p]
sdl.SDL_GetGamepadButton.restype = ctypes.c_bool
sdl.SDL_GetGamepadButton.argtypes = [ctypes.c_void_p, ctypes.c_int]
sdl.SDL_GetGamepadAxis.restype = ctypes.c_int16
sdl.SDL_GetGamepadAxis.argtypes = [ctypes.c_void_p, ctypes.c_int]

BUTTONS = ["A", "B", "X", "Y", "Vista", "Guia", "Menu", "L3", "R3", "LB", "RB", "arriba", "abajo", "izq", "der"]
AXES = ["LX", "LY", "RX", "RY", "LT", "RT"]

sdl.SDL_Init(0x200 | 0x2000)
time.sleep(1.0)
sdl.SDL_UpdateGamepads()
n = ctypes.c_int()
ids = sdl.SDL_GetGamepads(ctypes.byref(n))
pads = [sdl.SDL_OpenGamepad(ids[i]) for i in range(n.value)]
for p in pads:
    print("mando:", sdl.SDL_GetGamepadName(p).decode(errors="replace"), flush=True)

last = {}
end = time.time() + seconds
while time.time() < end:
    sdl.SDL_UpdateGamepads()
    for i, p in enumerate(pads):
        pressed = [b for k, b in enumerate(BUTTONS) if sdl.SDL_GetGamepadButton(p, k)]
        axes = {a: sdl.SDL_GetGamepadAxis(p, k) for k, a in enumerate(AXES)}
        moved = {a: v for a, v in axes.items() if abs(v) > 8000}
        state = (tuple(pressed), tuple(sorted(moved.items())))
        if last.get(i) != state:
            print(f"{time.strftime('%H:%M:%S')} mando{i}: botones={pressed or '-'} ejes={moved or '-'}", flush=True)
            last[i] = state
    time.sleep(0.02)
