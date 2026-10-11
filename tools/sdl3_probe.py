import ctypes, os, sys, time

dll_path = sys.argv[1]
if len(sys.argv) > 2:
    os.environ["SDL_GAMECONTROLLERCONFIG"] = sys.argv[2]
elif "SDL_GAMECONTROLLERCONFIG" in os.environ and not os.environ["SDL_GAMECONTROLLERCONFIG"]:
    del os.environ["SDL_GAMECONTROLLERCONFIG"]

sdl = ctypes.CDLL(dll_path)


class GUID(ctypes.Structure):
    _fields_ = [("data", ctypes.c_uint8 * 16)]


sdl.SDL_Init.restype = ctypes.c_bool
sdl.SDL_Init.argtypes = [ctypes.c_uint32]
sdl.SDL_GetJoysticks.restype = ctypes.POINTER(ctypes.c_uint32)
sdl.SDL_GetJoysticks.argtypes = [ctypes.POINTER(ctypes.c_int)]
sdl.SDL_GetJoystickNameForID.restype = ctypes.c_char_p
sdl.SDL_GetJoystickNameForID.argtypes = [ctypes.c_uint32]
sdl.SDL_GetJoystickGUIDForID.restype = GUID
sdl.SDL_GetJoystickGUIDForID.argtypes = [ctypes.c_uint32]
sdl.SDL_GUIDToString.argtypes = [GUID, ctypes.c_char_p, ctypes.c_int]
sdl.SDL_IsGamepad.restype = ctypes.c_bool
sdl.SDL_IsGamepad.argtypes = [ctypes.c_uint32]
sdl.SDL_GetGamepadMappingForID.restype = ctypes.c_char_p
sdl.SDL_GetGamepadMappingForID.argtypes = [ctypes.c_uint32]
sdl.SDL_GetError.restype = ctypes.c_char_p

if not sdl.SDL_Init(0x200 | 0x2000):
    sys.exit("SDL_Init fallo: " + sdl.SDL_GetError().decode())
time.sleep(1.5)
sdl.SDL_PumpEvents()

n = ctypes.c_int()
ids = sdl.SDL_GetJoysticks(ctypes.byref(n))
for i in range(n.value):
    jid = ids[i]
    buf = ctypes.create_string_buffer(64)
    sdl.SDL_GUIDToString(sdl.SDL_GetJoystickGUIDForID(jid), buf, 64)
    name = (sdl.SDL_GetJoystickNameForID(jid) or b"?").decode(errors="replace")
    print(f"«{name}» guid={buf.value.decode()} mando={sdl.SDL_IsGamepad(jid)}")
    if sdl.SDL_IsGamepad(jid):
        m = sdl.SDL_GetGamepadMappingForID(jid)
        print("   ", (m or b"").decode()[:120])
sdl.SDL_Quit()
