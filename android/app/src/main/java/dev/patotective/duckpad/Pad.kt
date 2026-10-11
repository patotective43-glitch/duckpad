package dev.patotective.duckpad

/** Bits de botón con los valores de XInput (XINPUT_GAMEPAD_*), la referencia de un mando de Xbox. */
object Buttons {
    const val DPAD_UP = 0x0001
    const val DPAD_DOWN = 0x0002
    const val DPAD_LEFT = 0x0004
    const val DPAD_RIGHT = 0x0008
    const val MENU = 0x0010
    const val VIEW = 0x0020
    const val LEFT_THUMB = 0x0040
    const val RIGHT_THUMB = 0x0080
    const val LB = 0x0100
    const val RB = 0x0200
    const val GUIDE = 0x0400
    const val A = 0x1000
    const val B = 0x2000
    const val X = 0x4000
    const val Y = 0x8000

    // Fuera de los 16 bits de XInput: los gatillos viajan aparte como valores 0..255.
    const val TRIGGER_L = 1 shl 16
    const val TRIGGER_R = 1 shl 17
}

/** Ejes en convención XInput: -32768..32767, Y positivo hacia arriba. Gatillos 0..255. */
data class PadState(
    val buttons: Int = 0,
    val lt: Int = 0,
    val rt: Int = 0,
    val lx: Int = 0,
    val ly: Int = 0,
    val rx: Int = 0,
    val ry: Int = 0,
) {
    companion object {
        val NEUTRAL = PadState()
    }
}
