package dev.patotective.duckpad

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import java.util.Locale

/** Tema, idioma del modo demo y nombre Bluetooth del mando. */
object Look {
    const val PAD_NAME = "DuckPad"

    fun demo(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && Prefs(context).demo

    /** Contexto con el tema elegido (solo si [themed]) y, en modo demo, en inglés. */
    fun wrap(base: Context, themed: Boolean): Context {
        val prefs = Prefs(base)
        val theme = if (themed) prefs.theme else 0
        val demo = demo(base)
        if (theme == 0 && !demo) return base
        val config = Configuration(base.resources.configuration)
        if (theme != 0) {
            val night = if (theme == 2) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
        if (demo) config.setLocales(LocaleList(Locale.US))
        return base.createConfigurationContext(config)
    }

    /** En las capturas de la tienda no sale el nombre del ordenador. */
    fun hostName(context: Context, name: String?): String = if (name == null || demo(context)) "PC" else name

    fun defaultSuffix(context: Context): String {
        val device = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.trim()
        val name = if (device.isNullOrEmpty() || device.startsWith(PAD_NAME)) Build.MODEL else device
        return name.trim().take(Prefs.MAX_SUFFIX).trim()
    }

    fun suffix(context: Context): String = Prefs(context).nameSuffix ?: defaultSuffix(context)

    /** «DuckPad» y el nombre del aparato: dos móviles con DuckPad en el mismo PC salen con nombres distintos. */
    fun padName(context: Context): String = suffix(context).let { if (it.isEmpty()) PAD_NAME else "$PAD_NAME $it" }
}
