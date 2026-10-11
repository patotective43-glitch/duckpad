# Google Play graphics

Upload in Play Console > Grow users > Store presence > Main store listing. The listing text is in [../store/listing.md](../store/listing.md).

| File | Where it goes | Size |
|---|---|---|
| `icon-512.png` | App icon | 512 × 512, PNG without transparency |
| `feature-graphic-en-1024x500.png` | Feature graphic (English, default) | 1024 × 500 |
| `feature-graphic-es-1024x500.png` | Feature graphic (Spanish translation) | 1024 × 500 |
| `phone/01-controller.png` … `04-home.png` | Phone screenshots | 1920 × 1080 and 1080 × 1920 |
| `tablet/01-controller.png` … `03-home.png` | 7" and 10" tablet screenshots | 1920 × 1200 |

Each screenshot puts a short English headline over the real app's screen (Realme 7 5G and Redmi Pad SE, connected to a Windows 11 PC) on the caramel background. The app is in English with the dark theme, taken in the debug build's demo mode so the computer's name shows as "PC". Status and navigation bars are cropped out, and nothing inside the app's screen is retouched.

To redo them, with the debug build installed and the PC paired:

```powershell
py capture.py <adb serial> phone <raw folder>
py capture.py <adb serial> tablet <raw folder>
py make_playstore.py <raw folder> <RobotoStatic-Regular.ttf>
```

`capture.py` closes every controller session with back twice and waits for the orderly teardown; it never force-stops the app. The crops in `make_playstore.py` are measured for those two devices.

The icon and feature graphics are drawn from the app icon (caramel `#8F4C14`, cream `#FFF1DA`, triggers `#F6C27A`) with Roboto (Apache 2.0). Light text on caramel has a contrast of 6:1 (title) and 4:1 (tagline, large text).
