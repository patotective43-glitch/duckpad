package dev.patotective.duckpad

import android.content.Context

// commit() en link_active y session_interrupted: tienen que estar en disco antes de que el proceso pueda morir.
@android.annotation.SuppressLint("ApplySharedPref")
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Set before exposing HID; cleared only after an orderly disconnect and teardown. */
    var sessionInterrupted: Boolean
        get() = sp.getBoolean("session_interrupted", false)
        set(v) { sp.edit().putBoolean("session_interrupted", v).commit() }

    /** Hay un PC enlazado con el mando. Si sigue a true al arrancar, el proceso murió con el enlace abierto. */
    var linkActive: Boolean
        get() = sp.getBoolean("link_active", false)
        set(v) { sp.edit().putBoolean("link_active", v).commit() }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    var portrait: Boolean
        get() = sp.getBoolean("portrait", false)
        set(v) = sp.edit().putBoolean("portrait", v).apply()

    /** Función opcional: los botones de volumen hacen de botones del mando mientras se juega. */
    var volumeEnabled: Boolean
        get() = sp.getBoolean("volume_enabled", false)
        set(v) = sp.edit().putBoolean("volume_enabled", v).apply()

    // Con los botones arriba al girar el móvil, Volumen + queda a la derecha (RT) y Volumen − a la izquierda (LT).
    var volumeUp: Int
        get() = sp.getInt("volume_up", Buttons.TRIGGER_R)
        set(v) = sp.edit().putInt("volume_up", v).apply()
    var volumeDown: Int
        get() = sp.getInt("volume_down", Buttons.TRIGGER_L)
        set(v) = sp.edit().putInt("volume_down", v).apply()

    /** Hasta la primera conexión con éxito la pantalla principal muestra los pasos desplegados. */
    var connectedOnce: Boolean
        get() = sp.getBoolean("connected_once", false)
        set(v) = sp.edit().putBoolean("connected_once", v).apply()

    /** Nombre Bluetooth del móvil guardado mientras dura la sesión, que lo cambia a «DuckPad». */
    var originalName: String?
        get() = sp.getString("original_name", null)
        set(v) = sp.edit().putString("original_name", v).apply()

    /** PCs que han usado el mando; el vínculo se conserva entre sesiones. */
    var peers: Set<String>
        get() = sp.getStringSet("peers", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("peers", v).apply()

    /** Último PC que emparejó el mando: si otro aparato vinculado se le adelanta, este le quita el mando. */
    var preferredHost: String?
        get() = sp.getString("preferred_host", null)
        set(v) = sp.edit().putString("preferred_host", v).apply()

    /**
     * Dos identidades guardadas (0 = A, 1 = B) y la que se usa. Windows guarda la del primer emparejamiento:
     * cambiar de una a otra exige quitar el mando en Windows y emparejar de nuevo.
     */
    var activeSlot: Int
        get() = sp.getInt("identity_slot", 0).coerceIn(0, 1)
        set(v) = sp.edit().putInt("identity_slot", v.coerceIn(0, 1)).apply()

    // La A conserva las claves de antes de que hubiera dos, para no perder la identidad ya guardada.
    fun identity(slot: Int): Identity {
        val p = if (slot == 1) "pnp_b_" else "pnp_"
        return Identity(sp.getInt("${p}vid", DEFAULT.vid), sp.getInt("${p}pid", DEFAULT.pid), sp.getInt("${p}version", DEFAULT.version))
    }

    fun saveIdentity(slot: Int, id: Identity) {
        val p = if (slot == 1) "pnp_b_" else "pnp_"
        sp.edit().putInt("${p}vid", id.vid).putInt("${p}pid", id.pid).putInt("${p}version", id.version).apply()
    }

    val activeIdentity get() = identity(activeSlot)

    companion object {
        // PID de pruebas privadas de pid.codes: no es único y no puede salir en una versión publicada.
        val DEFAULT = Identity(0x1209, 0x0001, 0x0100)
    }
}

/** PnP ID del servicio Device Information: VID, PID y versión. */
data class Identity(val vid: Int, val pid: Int, val version: Int) {
    override fun toString() = "%04X:%04X:%04X".format(vid, pid, version)
}
