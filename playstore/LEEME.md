# Material para Google Play

Todo listo para subir en Play Console > Presencia en Google Play > Ficha principal. Los textos están en [../store/ficha.md](../store/ficha.md).

| Archivo | Dónde va | Medidas |
|---|---|---|
| `icono-512.png` | Icono de la app | 512 × 512, PNG sin transparencia |
| `cabecera-es-1024x500.png` | Gráfico de cabecera (español) | 1024 × 500 |
| `cabecera-en-1024x500.png` | Gráfico de cabecera (inglés) | 1024 × 500 |
| `movil/01-mando.png` … `04-inicio.png` | Capturas de teléfono | 2:1 como máximo |
| `tablet/01-mando.png` … `03-inicio.png` | Capturas de tablet de 7" y de 10" | 16:10 y 16:9 aprox. |

Las capturas son de la app de verdad (Realme 7 5G y Redmi Pad SE, 11 de octubre de 2026), conectada a un PC con Windows 11. Las del móvil se han rellenado con el color de fondo de la app hasta 2:1, porque Google Play no admite capturas más alargadas; en las de la pantalla principal se han quitado las barras del sistema. Ninguna está retocada por dentro.

El icono y las cabeceras se generan con `make_playstore.py` a partir del icono de la app (caramelo `#8F4C14`, crema `#FFF1DA`, gatillos `#F6C27A`) y la fuente Roboto (Apache 2.0). El texto claro sobre el caramelo tiene un contraste de 6:1 (título) y 4:1 (frase, texto grande).

No hay capturas en inglés: harían falta con el móvil en inglés.
