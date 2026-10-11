import ctypes, sys, time
from ctypes import wintypes

winmm = ctypes.windll.winmm


class JOYCAPSW(ctypes.Structure):
    _fields_ = [("wMid", wintypes.WORD), ("wPid", wintypes.WORD), ("szPname", wintypes.WCHAR * 32),
                ("wXmin", wintypes.UINT), ("wXmax", wintypes.UINT), ("wYmin", wintypes.UINT), ("wYmax", wintypes.UINT),
                ("wZmin", wintypes.UINT), ("wZmax", wintypes.UINT), ("wNumButtons", wintypes.UINT),
                ("wPeriodMin", wintypes.UINT), ("wPeriodMax", wintypes.UINT), ("wRmin", wintypes.UINT), ("wRmax", wintypes.UINT),
                ("wUmin", wintypes.UINT), ("wUmax", wintypes.UINT), ("wVmin", wintypes.UINT), ("wVmax", wintypes.UINT),
                ("wCaps", wintypes.UINT), ("wMaxAxes", wintypes.UINT), ("wNumAxes", wintypes.UINT), ("wMaxButtons", wintypes.UINT),
                ("szRegKey", wintypes.WCHAR * 32), ("szOEMVxD", wintypes.WCHAR * 260)]


class JOYINFOEX(ctypes.Structure):
    _fields_ = [("dwSize", wintypes.DWORD), ("dwFlags", wintypes.DWORD), ("dwXpos", wintypes.DWORD), ("dwYpos", wintypes.DWORD),
                ("dwZpos", wintypes.DWORD), ("dwRpos", wintypes.DWORD), ("dwUpos", wintypes.DWORD), ("dwVpos", wintypes.DWORD),
                ("dwButtons", wintypes.DWORD), ("dwButtonNumber", wintypes.DWORD), ("dwPOV", wintypes.DWORD),
                ("dwReserved1", wintypes.DWORD), ("dwReserved2", wintypes.DWORD)]


def read(i):
    info = JOYINFOEX(dwSize=ctypes.sizeof(JOYINFOEX), dwFlags=0xFF)
    if winmm.joyGetPosEx(i, ctypes.byref(info)) != 0:
        return None
    return (info.dwXpos, info.dwYpos, info.dwZpos, info.dwRpos, info.dwUpos, info.dwVpos, hex(info.dwButtons), info.dwPOV)


for i in range(16):
    caps = JOYCAPSW()
    if winmm.joyGetDevCapsW(i, ctypes.byref(caps), ctypes.sizeof(caps)) == 0:
        print(i, f"VID {caps.wMid:04x} PID {caps.wPid:04x}", caps.wNumButtons, "botones", caps.wNumAxes, "ejes", read(i))

seconds = float(sys.argv[1]) if len(sys.argv) > 1 else 0
end = time.time() + seconds
last = {}
while time.time() < end:
    for i in range(16):
        v = read(i)
        if v is not None and last.get(i) != v:
            print(f"{time.strftime('%H:%M:%S')} joy{i}: X Y Z R U V = {v[:6]}  botones {v[6]}  cruceta {v[7]}", flush=True)
            last[i] = v
    time.sleep(0.05)
