"""Raw store screenshots from one device over ADB, with the debug build installed and the PC paired.

Usage: capture.py <adb serial> <phone|tablet> <output folder>

Turns on demo mode (English, "PC" instead of the computer's name) and the dark theme, takes the shots and puts
both settings back. Every controller session is closed with back twice and waits for the orderly teardown:
the app is never force-stopped while the PC is linked.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = str(Path.home() / r"AppData\Local\Android\Sdk\platform-tools\adb.exe")
PKG = "dev.patotective.duckpad"
serial, kind, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
out.mkdir(parents=True, exist_ok=True)


def adb(*a):
    return subprocess.run([ADB, "-s", serial, *map(str, a)], capture_output=True, encoding="utf8", errors="replace").stdout


def shot(label):
    data = subprocess.run([ADB, "-s", serial, "exec-out", "screencap", "-p"], capture_output=True).stdout
    (out / f"{kind}-{label}.png").write_bytes(data)
    print("shot", label, len(data), flush=True)


def dump():
    adb("shell", "uiautomator", "dump", "/data/local/tmp/s.xml")
    return ET.fromstring(adb("shell", "cat", "/data/local/tmp/s.xml"))


def bounds(n):
    return list(map(int, re.findall(r"\d+", n.get("bounds"))))


def find(root, rid=None, desc=None):
    for n in root.iter("node"):
        if rid and n.get("resource-id") == f"{PKG}:id/{rid}" or desc and n.get("content-desc") == desc:
            return n


def tap_node(n):
    l, t, r, b = bounds(n)
    adb("shell", "input", "tap", (l + r) // 2, (t + b) // 2)
    time.sleep(1)


def scroll_to(rid, top_third=False):
    """Scrolls the main screen until the view is on screen (or in its upper third)."""
    for i in range(24):
        root = dump()
        w, h = bounds(root.find("node"))[2:]
        n = find(root, rid)
        if n is not None:
            t = bounds(n)[1]
            if 0 < t < (h // 3 if top_third else h - 150):
                return n
        # Down first; after 12 tries, back up.
        y1, y2 = (h * 3 // 4, h // 4) if i < 12 else (h // 4, h * 3 // 4)
        adb("shell", "input", "swipe", w // 2, y1, w // 2, y2, 400)
        time.sleep(0.6)
    sys.exit(f"{rid} not found")


def scroll_top():
    w, h = bounds(dump().find("node"))[2:]
    for _ in range(4):
        adb("shell", "input", "swipe", w // 2, h // 4, w // 2, h * 3 // 4, 200)
    time.sleep(1)


def tap(rid):
    tap_node(scroll_to(rid))


def wait_log(texts, seconds):
    end = time.time() + seconds
    while time.time() < end:
        log = adb("logcat", "-d", "-s", "DuckPadBLE")
        if any(t in log for t in texts):
            return True
        time.sleep(0.5)
    return False


def main_screen(*extra):
    # Single top: if the main screen is already open, the extras still reach it through onNewIntent.
    adb("shell", "am", "start", "--activity-single-top", "-n", f"{PKG}/.MainActivity", *extra)
    time.sleep(2.5)


def session(label):
    adb("logcat", "-c")
    tap("session_start")
    if not wait_log(("notificaciones enviadas", "envÃ­o en marcha"), 120):
        print("the PC did not connect: no shot", label)
    else:
        time.sleep(4)
        shot(label)
    adb("logcat", "-c")
    adb("shell", "input", "keyevent", "BACK")
    time.sleep(0.3)
    adb("shell", "input", "keyevent", "BACK")
    print("orderly close:", wait_log(("enlace cortado, cerrando el servidor",), 30), flush=True)
    time.sleep(1.5)


prefs = adb("shell", "run-as", PKG, "cat", "shared_prefs/settings.xml")
was_portrait = 'name="portrait" value="true"' in prefs
theme = re.search(r'name="theme" value="(\d)"', prefs)
old_theme = theme.group(1) if theme else "0"

main_screen("--ez", "demo", "true", "--ei", "theme", "2")
tap("landscape")

main_screen()
scroll_top()
shot("home")

session("controller")

# Editor with a control picked, so the size row shows.
main_screen()
tap("edit_layout")
time.sleep(2)
y = find(dump(), desc="Y")
if y is not None:
    tap_node(y)
    bigger = find(dump(), desc="Make bigger")
    for _ in range(2):
        tap_node(bigger)
shot("editor")
adb("shell", "input", "keyevent", "BACK")
time.sleep(1)
for n in dump().iter("node"):
    if n.get("resource-id") == "android:id/button1":
        tap_node(n)
        break

if kind == "phone":
    main_screen()
    tap("portrait")
    session("controller-portrait")
    main_screen()
    tap("landscape")

main_screen()
if was_portrait:
    tap("portrait")
main_screen("--ez", "demo", "false", "--ei", "theme", old_theme)
print("theme back to", old_theme, "| orientation", "portrait" if was_portrait else "landscape")
