package dev.patotective.duckpad

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Las dos filas del mando tal como salen en Agregar dispositivo de Windows, con una etiqueta al lado de cada una
 * en los colores del tema. Los iconos son un recorte de una captura real; el nombre se escribe encima porque la
 * captura es de cuando la app se llamaba de otra forma.
 */
class PairingChoiceView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val screenshot = BitmapFactory.decodeResource(resources, R.drawable.pairing_windows)
    private val d = resources.displayMetrics.density
    private val rowHeight = 40 * d
    private val scale = rowHeight / ROW_PX
    private val name = context.getString(R.string.app_name)
    private val windowsText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WINDOWS_TEXT
        textSize = NAME_PX * scale
    }
    private val shotWidth = (NAME_X - CROP.left + RIGHT_PAD) * scale + windowsText.measureText(name)
    private val clip = Path()
    private val shot = RectF()
    private val icons = RectF()
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14 * resources.displayMetrics.scaledDensity }
    private val good = context.getColor(R.color.accent)
    private val bad = context.getColor(R.color.error)

    init {
        contentDescription = context.getString(R.string.guide_accessibility)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), (2 * rowHeight).toInt())
    }

    override fun onDraw(c: Canvas) {
        shot.set(0f, 0f, shotWidth, 2 * rowHeight)
        clip.reset()
        clip.addRoundRect(shot, 8 * d, 8 * d, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        c.drawColor(WINDOWS_BACKGROUND)
        icons.set(0f, 0f, CROP.width() * scale, 2 * rowHeight)
        c.drawBitmap(screenshot, CROP, icons, image)
        val nameX = (NAME_X - CROP.left) * scale
        for (row in 0..1) {
            val centerY = rowHeight * (row + 0.5f)
            c.drawText(name, nameX, centerY - (windowsText.descent() + windowsText.ascent()) / 2, windowsText)
        }
        c.restore()
        val x = shotWidth + 16 * d
        drawLabel(c, R.string.choose_device, good, true, x, rowHeight / 2)
        drawLabel(c, R.string.avoid_phone, bad, false, x, rowHeight * 1.5f)
    }

    private fun drawLabel(c: Canvas, text: Int, color: Int, bold: Boolean, x: Float, centerY: Float) {
        label.color = color
        label.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(context.getString(text), x, centerY - (label.descent() + label.ascent()) / 2, label)
    }

    private companion object {
        // La imagen solo guarda los iconos de las dos filas: dos dispositivos arriba y teléfono abajo.
        val CROP = Rect(0, 0, 36, 96)
        const val ROW_PX = 48f
        // Dónde empezaba el nombre en la captura, su tamaño y los colores de ese diálogo de Windows.
        const val NAME_X = 45f
        const val NAME_PX = 15f
        const val RIGHT_PAD = 16f
        val WINDOWS_BACKGROUND = Color.rgb(40, 40, 40)
        val WINDOWS_TEXT = Color.WHITE
    }
}
