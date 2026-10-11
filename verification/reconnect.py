"""Ciclos de cierre ordenado y reconexión de DuckPad con la identidad propia.
Cada ciclo: atrás dos veces, espera a «enlace cortado», mata el proceso (ya sin enlace), abre la app en frío,
toca Iniciar sesión y mide hasta que el PC vuelve a usar el mando y Windows vuelve a tener el nodo HID.
No mira XInput: hay un mando Xbox real emparejado en el mismo PC."""
import json, re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path

ADB = str(Path.home() / r"AppData\Local\Android\Sdk\platform-tools\adb.exe")
PKG = "dev.patotective.duckpad"
CYCLES = int(sys.argv[1]) if len(sys.argv) > 1 else 3
# Patrón del nodo HID en Windows; por defecto, la identidad de DuckPad.
HID_LIKE = sys.argv[2] if len(sys.argv) > 2 else "HID*VID&021209_PID&0001*"


def adb(*args):
    return subprocess.run([ADB, *map(str, args)], capture_output=True, encoding="utf8", errors="replace").stdout


def log():
    return adb("logcat", "-d", "-s", "DuckPadBLE")


def hid_present():
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
                          "@(Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -like '" + HID_LIKE + "' }).Count"],
                         capture_output=True, encoding="utf8").stdout.strip()
    return out not in ("", "0")


def wait_for(check, seconds):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if check():
            return True
        time.sleep(0.2)
    return False


def tap_start():
    adb("shell", "uiautomator", "dump", "/data/local/tmp/duckpad.xml")
    root = ET.fromstring(adb("shell", "cat", "/data/local/tmp/duckpad.xml"))
    for n in root.iter("node"):
        if n.get("resource-id") == f"{PKG}:id/session_start" and n.get("enabled") == "true":
            l, t, r, b = map(int, re.findall(r"\d+", n.get("bounds")))
            adb("shell", "input", "tap", (l + r) // 2, (t + b) // 2)
            return
    raise RuntimeError("no encuentro Iniciar sesión activo")


results = []
for cycle in range(1, CYCLES + 1):
    adb("shell", "input", "keyevent", 4)
    time.sleep(0.3)
    adb("shell", "input", "keyevent", 4)
    closed = wait_for(lambda: "enlace cortado, cerrando el servidor" in log(), 15)
    prefs = adb("shell", "run-as", PKG, "cat", "shared_prefs/settings.xml")
    link_off = 'name="link_active" value="false"' in prefs
    if not (closed and link_off):
        sys.exit(f"ciclo {cycle}: la sesión no se cerró en orden; no mato el proceso")
    adb("shell", "am", "force-stop", PKG)
    hid_gone = wait_for(lambda: not hid_present(), 10)
    time.sleep(3)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(1.5)
    t0 = time.monotonic()
    tap_start()
    advertising = wait_for(lambda: "anunciando" in log(), 15)
    t_adv = time.monotonic()
    claimed = wait_for(lambda: "es el PC del mando" in log(), 40)
    t_claim = time.monotonic()
    hid_back = wait_for(hid_present, 40)
    t_hid = time.monotonic()
    result = {
        "ciclo": cycle,
        "cierre_ordenado": closed,
        "nodo_hid_desaparece_al_cerrar": hid_gone,
        "anuncio": advertising,
        "pc_reconecta": claimed,
        "segundos_desde_anuncio_hasta_pc": round(t_claim - t_adv, 2) if claimed else None,
        "nodo_hid_vuelve": hid_back,
        "segundos_desde_toque_hasta_nodo_hid": round(t_hid - t0, 2) if hid_back else None,
    }
    results.append(result)
    print(result, flush=True)
    if not (claimed and hid_back):
        break
    time.sleep(5)

Path(__file__).with_name("reconnect-results.json").write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf8")
