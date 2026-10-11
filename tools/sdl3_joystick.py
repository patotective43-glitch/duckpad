"""Joysticks en bruto vistos por una SDL3 (índices de botones, ejes y crucetas) para escribir una correspondencia
de SDL_GameControllerDB. Uso: sdl3_joystick.py <ruta SDL3.dll> <segundos> [VID hex] [--rumble]
Con VID solo abre los mandos de ese fabricante (así no se mezcla un mando Xbox real emparejado)."""
import ctypes, os, sys, time

args = [a for a in sys.argv[1:] if not a.startswith("--")]
rumble = "--rumble" in sys.argv
dll_path, seconds = args[0], float(args[1])
only_vid = int(args[2], 16) if len(args) > 2 else None
if "SDL_GAMECONTROLLERCONFIG" in os.environ and not os.environ["SDL_GAMECONTROLLERCONFIG"]:
    del os.environ["SDL_GAMECONTROLLERCONFIG"]

sdl = ctypes.CDLL(dll_path)


class GUID(ctypes.Structure):
    _fields_ = [("data", ctypes.c_uint8 * 16)]


P = ctypes.c_void_p
sdl.SDL_Init.restype = ctypes.c_bool
sdl.SDL_GetError.restype = ctypes.c_char_p
sdl.SDL_GetJoysticks.restype = ctypes.POINTER(ctypes.c_uint32)
sdl.SDL_OpenJoystick.restype = P
sdl.SDL_OpenJoystick.argtypes = [ctypes.c_uint32]
for name, res in [("SDL_GetJoystickName", ctypes.c_char_p), ("SDL_GetJoystickPath", ctypes.c_char_p),
                  ("SDL_GetNumJoystickAxes", ctypes.c_int), ("SDL_GetNumJoystickButtons", ctypes.c_int),
                  ("SDL_GetNumJoystickHats", ctypes.c_int), ("SDL_GetJoystickVendor", ctypes.c_uint16),
                  ("SDL_GetJoystickProduct", ctypes.c_uint16), ("SDL_GetJoystickProductVersion", ctypes.c_uint16),
                  ("SDL_GetJoystickGUID", GUID), ("SDL_GetJoystickID", ctypes.c_uint32)]:
    getattr(sdl, name).restype = res
    getattr(sdl, name).argtypes = [P]
sdl.SDL_GetJoystickAxis.restype = ctypes.c_int16
sdl.SDL_GetJoystickAxis.argtypes = [P, ctypes.c_int]
sdl.SDL_GetJoystickButton.restype = ctypes.c_bool
sdl.SDL_GetJoystickButton.argtypes = [P, ctypes.c_int]
sdl.SDL_GetJoystickHat.restype = ctypes.c_uint8
sdl.SDL_GetJoystickHat.argtypes = [P, ctypes.c_int]
sdl.SDL_GUIDToString.argtypes = [GUID, ctypes.c_char_p, ctypes.c_int]
sdl.SDL_IsGamepad.restype = ctypes.c_bool
sdl.SDL_IsGamepad.argtypes = [ctypes.c_uint32]
sdl.SDL_GetGamepadMappingForID.restype = ctypes.c_char_p
sdl.SDL_GetGamepadMappingForID.argtypes = [ctypes.c_uint32]
sdl.SDL_RumbleJoystick.restype = ctypes.c_bool
sdl.SDL_RumbleJoystick.argtypes = [P, ctypes.c_uint16, ctypes.c_uint16, ctypes.c_uint32]

if not sdl.SDL_Init(0x200 | 0x2000):
    sys.exit("SDL_Init falló: " + sdl.SDL_GetError().decode())
time.sleep(1.5)
sdl.SDL_UpdateJoysticks()

n = ctypes.c_int()
ids = sdl.SDL_GetJoysticks(ctypes.byref(n))
pads = []
for i in range(n.value):
    j = sdl.SDL_OpenJoystick(ids[i])
    if not j:
        continue
    vid = sdl.SDL_GetJoystickVendor(j)
    buf = ctypes.create_string_buffer(64)
    sdl.SDL_GUIDToString(sdl.SDL_GetJoystickGUID(j), buf, 64)
    counts = (sdl.SDL_GetNumJoystickAxes(j), sdl.SDL_GetNumJoystickButtons(j), sdl.SDL_GetNumJoystickHats(j))
    jid = sdl.SDL_GetJoystickID(j)
    print(f"«{(sdl.SDL_GetJoystickName(j) or b'?').decode(errors='replace')}» VID {vid:04X} PID {sdl.SDL_GetJoystickProduct(j):04X} "
          f"versión {sdl.SDL_GetJoystickProductVersion(j):04X} guid={buf.value.decode()} ejes={counts[0]} botones={counts[1]} "
          f"crucetas={counts[2]} mando={sdl.SDL_IsGamepad(jid)}", flush=True)
    print("   ruta:", (sdl.SDL_GetJoystickPath(j) or b"").decode(errors="replace"))
    if sdl.SDL_IsGamepad(jid):
        print("   correspondencia:", (sdl.SDL_GetGamepadMappingForID(jid) or b"").decode())
    if only_vid is None or vid == only_vid:
        pads.append((j, counts))
        if rumble:
            ok = sdl.SDL_RumbleJoystick(j, 0xFFFF, 0xFFFF, 1500)
            print("   vibración:", "aceptada" if ok else "rechazada: " + sdl.SDL_GetError().decode(errors="replace"), flush=True)

last = {}
end = time.time() + seconds
while time.time() < end:
    sdl.SDL_UpdateJoysticks()
    for k, (j, (na, nb, nh)) in enumerate(pads):
        buttons = tuple(b for b in range(nb) if sdl.SDL_GetJoystickButton(j, b))
        axes = tuple((a, sdl.SDL_GetJoystickAxis(j, a)) for a in range(na))
        hats = tuple(sdl.SDL_GetJoystickHat(j, h) for h in range(nh))
        # Los ejes se redondean para no imprimir cada temblor del dedo.
        state = (buttons, tuple((a, round(v / 4096)) for a, v in axes), hats)
        if last.get(k) != state:
            print(f"{time.strftime('%H:%M:%S')} botones={list(buttons)} ejes={[v for _, v in axes]} crucetas={list(hats)}", flush=True)
            last[k] = state
    time.sleep(0.01)
sdl.SDL_Quit()
