package dev.patotective.duckpad

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher

/** Editor de posiciones: no arranca el Bluetooth ni manda nada al PC. */
class LayoutActivity : Activity() {
    private lateinit var pad: GamepadView
    private lateinit var bar: LinearLayout
    private lateinit var sizeRow: LinearLayout
    private lateinit var sizeLabel: TextView

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(Look.wrap(newBase, themed = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val portrait = Prefs(this).portrait
        requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val d = resources.displayMetrics.density

        pad = GamepadView(this).apply {
            editing = true
            status = getString(R.string.layout_hint)
            // La barra se aparta mientras se arrastra, por si el control pasa por debajo.
            onDragging = { dragging -> bar.animate().alpha(if (dragging) 0f else 1f).setDuration(120).start() }
            onSelectionChanged = { scale -> showSize(scale) }
        }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(GamepadView.SURFACE)
                setStroke((1 * d).toInt(), GamepadView.BORDER)
                cornerRadius = 12 * d
            }
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        // Segunda fila, solo con un control elegido: el tamaño no cabe al lado de las acciones en un móvil en vertical.
        sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        bar.addView(actions)
        bar.addView(sizeRow, LinearLayout.LayoutParams(-1, -2))
        fun action(label: Int, color: Int, bold: Boolean = false, row: LinearLayout = actions, onClick: () -> Unit) = Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless).apply {
            setText(label)
            isAllCaps = false
            textSize = 14f
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            minWidth = 0
            minimumWidth = 0
            minHeight = (48 * d).toInt()
            minimumHeight = minHeight
            setPadding((12 * d).toInt(), 0, (12 * d).toInt(), 0)
            setOnClickListener { onClick() }
            row.addView(this)
        }
        action(R.string.layout_cancel, GamepadView.LABEL) { confirmDiscard() }
        action(R.string.layout_reset, GamepadView.LABEL) {
            AlertDialog.Builder(this).setMessage(R.string.layout_reset_confirm)
                .setNegativeButton(R.string.layout_cancel, null)
                .setPositiveButton(R.string.layout_reset) { _, _ -> pad.resetLayout() }.show()
        }
        action(R.string.layout_save, GamepadView.CARAMEL, bold = true) { pad.saveLayout(); finish() }
        // Si un control queda debajo de la barra, la barra salta al borde contrario.
        action(R.string.layout_move_bar, GamepadView.LABEL) {
            val params = bar.layoutParams as FrameLayout.LayoutParams
            val atTop = params.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.TOP
            params.gravity = Gravity.CENTER_HORIZONTAL or if (atTop) Gravity.BOTTOM else Gravity.TOP
            bar.layoutParams = params
        }.apply { contentDescription = getString(R.string.move_toolbar) }
        action(R.string.size_smaller, GamepadView.LABEL, bold = true, row = sizeRow) { pad.stepSelected(-1) }
            .apply { contentDescription = getString(R.string.size_smaller_a11y); minWidth = (48 * d).toInt(); textSize = 22f }
        sizeLabel = TextView(this).apply {
            setTextColor(GamepadView.LABEL)
            textSize = 14f
            gravity = Gravity.CENTER
            minWidth = (96 * d).toInt()
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            sizeRow.addView(this)
        }
        action(R.string.size_bigger, GamepadView.LABEL, bold = true, row = sizeRow) { pad.stepSelected(1) }
            .apply { contentDescription = getString(R.string.size_bigger_a11y); minWidth = (48 * d).toInt(); textSize = 22f }

        val margin = (8 * d).toInt()
        val root = FrameLayout(this)
        root.addView(pad)
        // Huecos libres de la distribución inicial: apaisado, arriba entre LB y RB; en vertical, debajo de la cruceta.
        root.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or if (portrait) Gravity.BOTTOM else Gravity.TOP).apply {
            setMargins(margin, margin, margin, margin)
        })
        setContentView(root)
        // Con targetSdk 36 Android ya no llama a onBackPressed en Android 13 o posterior.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { confirmDiscard() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun showSize(scale: Float?) {
        sizeRow.visibility = if (scale == null) View.GONE else View.VISIBLE
        // Con la fila de tamaño la barra crece y taparía la ayuda, que ya no hace falta con un control elegido.
        pad.status = if (scale == null) getString(R.string.layout_hint) else ""
        if (scale != null) sizeLabel.text = getString(R.string.size_value, Math.round(scale * 100))
    }

    @Deprecated("Android 12 o anterior; desde Android 13 el gesto llega por OnBackInvokedCallback.")
    override fun onBackPressed() = confirmDiscard()

    private fun confirmDiscard() {
        AlertDialog.Builder(this).setMessage(R.string.layout_discard)
            .setNegativeButton(R.string.layout_cancel, null)
            .setPositiveButton(R.string.layout_discard_ok) { _, _ -> finish() }.show()
    }
}
