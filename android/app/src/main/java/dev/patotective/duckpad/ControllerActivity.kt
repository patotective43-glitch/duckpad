package dev.patotective.duckpad

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import android.window.OnBackInvokedDispatcher

class ControllerActivity : Activity(), BlePad.Listener {

    private lateinit var pad: GamepadView
    private lateinit var prefs: Prefs
    private lateinit var vibrator: Vibrator
    private var device: BluetoothDevice? = null
    private var pcName = ""
    private val ble = true

    private val main = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var lastAttempt = 0L
    private var backPressedAt = 0L
    private var acquired = false
    private var rumbling = false

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Look.wrap(newBase, themed = false))

    private val statusTick = object : Runnable {
        override fun run() {
            updateStatus()
            main.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        BlePad.acquire(this)
        acquired = true
        startForegroundService(Intent(this, SessionService::class.java))
        requestedOrientation = if (prefs.portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        pad = GamepadView(this)
        pad.onStateChanged = { BlePad.update(it) }
        if (prefs.haptics) pad.onButtonDown = { tick() }
        if (ble) BlePad.onRumble = { strength, ms -> rumble(strength, ms) }
        setContentView(pad)
        // Con targetSdk 36 Android ya no llama a onBackPressed en Android 13 o posterior.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        }
        if (ble) BlePad.addListener(this)
    }

    override fun onBleChanged() {
        if (acquired && ble && !BlePad.sessionOpen) finish()
    }

    override fun onStart() {
        super.onStart()
        if (!::pad.isInitialized) return
        pad.reloadLayout()
        startedAt = SystemClock.uptimeMillis()
        main.post(statusTick)
    }

    override fun onStop() {
        super.onStop()
        main.removeCallbacks(statusTick)
        if (::pad.isInitialized) pad.releaseAll()
        if (::vibrator.isInitialized) rumble(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!acquired) return
        BlePad.removeListener(this)
        BlePad.onRumble = null
        BlePad.release()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = event.keyCode
        if (code != android.view.KeyEvent.KEYCODE_VOLUME_UP && code != android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            return super.dispatchKeyEvent(event)
        val action = when {
            !prefs.volumeEnabled -> 0
            code == android.view.KeyEvent.KEYCODE_VOLUME_UP -> prefs.volumeUp
            else -> prefs.volumeDown
        }
        if (action == 0 || !::pad.isInitialized) return super.dispatchKeyEvent(event)
        when (event.action) {
            android.view.KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                pad.setHardwareButton(code, action, true)
                if (prefs.haptics) tick()
            }
            android.view.KeyEvent.ACTION_UP -> pad.setHardwareButton(code, action, false)
        }
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else if (::pad.isInitialized) pad.releaseAll()
    }

    @Deprecated("Android 12 o anterior; desde Android 13 el gesto llega por OnBackInvokedCallback.")
    override fun onBackPressed() = handleBack()

    // Hace falta pulsar atrás dos veces: salir del mando termina la sesión y un toque suelto no debe cortarla.
    private fun handleBack() {
        val now = SystemClock.uptimeMillis()
        if (now - backPressedAt < 2000) {
            finish()
        } else {
            backPressedAt = now
            Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show()
        }
    }

    /** Fuerza 0..100 tal como la manda el PC; 0 para. La duración se limita por si se pierde la orden de parar. */
    private fun rumble(strength: Int, ms: Long = 0) {
        if (strength <= 0) {
            if (rumbling) vibrator.cancel()
            rumbling = false
            return
        }
        rumbling = true
        val amplitude = if (vibrator.hasAmplitudeControl()) (strength * 255 / 100).coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(ms.coerceIn(100, 30_000), amplitude))
    }

    private fun tick() {
        if (rumbling) return
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(12, 80))
        }
    }

    private fun updateStatus() = updateBleStatus()

    @SuppressLint("MissingPermission")
    private fun updateBleStatus() {
        when (BlePad.state) {
            BlePad.State.STARTING -> {
                pad.status = getString(R.string.status_ble_starting)
                pad.statusIsError = false
            }
            BlePad.State.ADVERTISING -> when {
                BlePad.wrongEntry -> {
                    pad.status = getString(R.string.status_ble_wrong)
                    pad.statusIsError = true
                }
                BlePad.staleBond -> {
                    pad.status = getString(R.string.status_ble_stale)
                    pad.statusIsError = true
                }
                BlePad.pcLostController() -> {
                    pad.status = getString(R.string.status_ble_lost)
                    pad.statusIsError = true
                }
                BlePad.pairedPcSilent() -> {
                    pad.status = getString(R.string.status_ble_silent)
                    pad.statusIsError = true
                }
                else -> {
                    pad.status = getString(R.string.status_ble_waiting)
                    pad.statusIsError = false
                }
            }
            BlePad.State.CONNECTED -> if (!BlePad.inputReady) {
                pad.status = getString(if (BlePad.connectionUnconfirmed) R.string.status_ble_generic else R.string.status_ble_checking)
                pad.statusIsError = true
            } else {
                // Los pasos se pliegan cuando Windows ya acepta los informes del mando, no con cualquier conexión.
                if (!prefs.connectedOnce) prefs.connectedOnce = true
                pad.status = getString(R.string.status_bt_connected, Look.hostName(this, BlePad.connectedName))
                pad.statusIsError = false
            }
            BlePad.State.FAILED -> {
                pad.status = getString(R.string.status_ble_failed, BlePad.failure)
                pad.statusIsError = true
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_BT_ADDRESS = "bt_address"
        const val EXTRA_BLE = "ble"

    }
}
