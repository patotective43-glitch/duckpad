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
import android.window.OnBackInvokedDispatcher

/** Editor de posiciones: no arranca el Bluetooth ni manda nada al PC. */
class LayoutActivity : Activity() {
    private lateinit var pad: GamepadView
    private lateinit var bar: LinearLayout

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
        }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(GamepadView.SURFACE)
                setStroke((1 * d).toInt(), GamepadView.BORDER)
                cornerRadius = 12 * d
            }
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
        }
        fun action(label: Int, color: Int, bold: Boolean = false, onClick: () -> Unit) = Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless).apply {
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
            bar.addView(this)
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

    @Deprecated("Android 12 o anterior; desde Android 13 el gesto llega por OnBackInvokedCallback.")
    override fun onBackPressed() = confirmDiscard()

    private fun confirmDiscard() {
        AlertDialog.Builder(this).setMessage(R.string.layout_discard)
            .setNegativeButton(R.string.layout_cancel, null)
            .setPositiveButton(R.string.layout_discard_ok) { _, _ -> finish() }.show()
    }
}
