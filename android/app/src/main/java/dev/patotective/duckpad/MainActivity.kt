package dev.patotective.duckpad

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var action: Button
    private lateinit var end: Button
    private val main = Handler(Looper.getMainLooper())
    private var lastStatus = ""
    private var stepsOpen = false
    private var advancedOpen = false
    private var identityFields: List<EditText> = emptyList()
    private val tick = object : Runnable {
        override fun run() { updateSession(); main.postDelayed(this, 500) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        status = findViewById(R.id.session_status)
        start = findViewById(R.id.session_start)
        action = findViewById(R.id.session_action)
        end = findViewById(R.id.session_end)
        start.setOnClickListener {
            startActivity(Intent(this, ControllerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        end.setOnClickListener { BlePad.endSession() }
        findViewById<Button>(R.id.edit_layout).setOnClickListener {
            startActivity(Intent(this, LayoutActivity::class.java))
        }
        findViewById<RadioGroup>(R.id.orientation).apply {
            check(if (prefs.portrait) R.id.portrait else R.id.landscape)
            setOnCheckedChangeListener { _, id -> prefs.portrait = id == R.id.portrait }
        }
        findViewById<Switch>(R.id.haptics).apply {
            isChecked = prefs.haptics
            setOnCheckedChangeListener { _, on -> prefs.haptics = on }
        }
        bindMapping(R.id.volume_up, true)
        bindMapping(R.id.volume_down, false)
        val volumeBox = findViewById<View>(R.id.volume_box)
        findViewById<Switch>(R.id.volume_enable).apply {
            isChecked = prefs.volumeEnabled
            volumeBox.visibility = if (isChecked) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, on ->
                prefs.volumeEnabled = on
                volumeBox.visibility = if (on) View.VISIBLE else View.GONE
            }
        }
        val steps = findViewById<View>(R.id.steps_box)
        val toggle = findViewById<Button>(R.id.steps_toggle)
        stepsOpen = savedInstanceState?.getBoolean("steps_open") ?: !prefs.connectedOnce
        fun showSteps() {
            steps.visibility = if (stepsOpen) View.VISIBLE else View.GONE
            toggle.setText(if (stepsOpen) R.string.steps_hide else R.string.steps_show)
        }
        toggle.setOnClickListener { stepsOpen = !stepsOpen; showSteps() }
        showSteps()
        bindIdentity(savedInstanceState)
        applyInsets()
    }
    private fun bindIdentity(saved: Bundle?) {
        advancedOpen = saved?.getBoolean("advanced_open") ?: false
        val box = findViewById<View>(R.id.advanced_box)
        val toggle = findViewById<Button>(R.id.advanced_toggle)
        fun show() {
            box.visibility = if (advancedOpen) View.VISIBLE else View.GONE
            toggle.setText(if (advancedOpen) R.string.advanced_hide else R.string.advanced_show)
        }
        toggle.setOnClickListener { advancedOpen = !advancedOpen; show() }
        show()
        identityFields = listOf(R.id.identity_vid, R.id.identity_pid, R.id.identity_version).map { findViewById(it) }
        val feedback = findViewById<TextView>(R.id.identity_feedback)
        val slots = findViewById<RadioGroup>(R.id.identity_slot)
        val slotButtons = listOf<RadioButton>(findViewById(R.id.identity_slot_a), findViewById(R.id.identity_slot_b))
        fun slotName(slot: Int) = getString(if (slot == 0) R.string.identity_slot_a else R.string.identity_slot_b)
        fun labelSlots() = slotButtons.forEachIndexed { slot, button ->
            button.text = getString(R.string.identity_slot_entry, slotName(slot), prefs.identity(slot).toString())
        }
        fun fill() {
            val id = prefs.activeIdentity
            listOf(id.vid, id.pid, id.version).forEachIndexed { i, v -> identityFields[i].setText(getString(R.string.hex4, v)); identityFields[i].error = null }
        }
        fun report(text: String) {
            feedback.text = text
            feedback.visibility = View.VISIBLE
        }
        labelSlots()
        slots.check(slotButtons[prefs.activeSlot].id)
        // Al recrear la actividad los campos recuperan solos lo que se estaba escribiendo.
        if (saved == null) fill()
        slots.setOnCheckedChangeListener { _, checked ->
            val slot = slotButtons.indexOfFirst { it.id == checked }
            if (slot < 0 || slot == prefs.activeSlot) return@setOnCheckedChangeListener
            prefs.activeSlot = slot
            fill()
            report(getString(R.string.identity_switched, slotName(slot), prefs.activeIdentity.toString()))
        }
        findViewById<Button>(R.id.identity_save).setOnClickListener {
            val values = identityFields.map { field ->
                field.text.toString().trim().takeIf { it.length in 1..4 }?.toIntOrNull(16)
                    .also { field.error = if (it == null) getString(R.string.identity_invalid) else null }
            }
            if (values.any { it == null }) {
                report(getString(R.string.identity_invalid))
                return@setOnClickListener
            }
            prefs.saveIdentity(prefs.activeSlot, Identity(values[0]!!, values[1]!!, values[2]!!))
            fill()
            labelSlots()
            report(getString(R.string.identity_saved, slotName(prefs.activeSlot), prefs.activeIdentity.toString()))
        }
        findViewById<Button>(R.id.identity_reset).setOnClickListener {
            prefs.saveIdentity(prefs.activeSlot, Prefs.DEFAULT)
            fill()
            labelSlots()
            report(getString(R.string.identity_reset_done, slotName(prefs.activeSlot), prefs.activeIdentity.toString()))
        }
    }
    private fun bindMapping(id: Int, up: Boolean) {
        val spinner = findViewById<Spinner>(id)
        val values = intArrayOf(0, Buttons.TRIGGER_L, Buttons.TRIGGER_R, Buttons.LB, Buttons.RB, Buttons.A, Buttons.B,
            Buttons.X, Buttons.Y, Buttons.LEFT_THUMB, Buttons.RIGHT_THUMB, Buttons.DPAD_UP,
            Buttons.DPAD_DOWN, Buttons.DPAD_LEFT, Buttons.DPAD_RIGHT, Buttons.VIEW, Buttons.MENU, Buttons.GUIDE)
        val labels = arrayOf(getString(R.string.volume_normal), "LT", "RT", "LB", "RB", "A", "B", "X", "Y", "L3", "R3",
            getString(R.string.map_up), getString(R.string.map_down), getString(R.string.map_left), getString(R.string.map_right),
            getString(R.string.label_view), getString(R.string.label_menu), getString(R.string.label_guide))
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(values.indexOf(if (up) prefs.volumeUp else prefs.volumeDown).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, itemId: Long) {
                if (up) prefs.volumeUp = values[position] else prefs.volumeDown = values[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }
    override fun onStart() {
        super.onStart()
        if (!BlePad.sessionOpen && hasPermission()) BlePad.restoreName(this)
        main.post(tick)
    }
    override fun onStop() { main.removeCallbacks(tick); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("steps_open", stepsOpen)
        outState.putBoolean("advanced_open", advancedOpen)
        super.onSaveInstanceState(outState)
    }
    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
         checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)
    @android.annotation.SuppressLint("MissingPermission")
    private fun updateSession() {
        val granted = hasPermission()
        val enabled = granted && getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        val message = when {
            !granted -> getString(R.string.bt_state_no_permission)
            !enabled -> getString(R.string.bt_state_off)
            BlePad.sessionOpen && BlePad.inputReady -> getString(R.string.session_connected, BlePad.connectedName ?: "PC")
            BlePad.sessionOpen && BlePad.connectionUnconfirmed -> getString(R.string.status_ble_generic)
            BlePad.sessionOpen && BlePad.linked -> getString(R.string.status_ble_checking)
            BlePad.sessionOpen && BlePad.pcLostController() -> getString(R.string.status_ble_lost)
            BlePad.sessionOpen && BlePad.pairedPcSilent() -> getString(R.string.status_ble_silent)
            BlePad.sessionOpen -> getString(R.string.session_waiting)
            !BlePad.idle -> getString(R.string.session_closing)
            prefs.linkActive -> getString(R.string.session_lost)
            prefs.sessionInterrupted -> getString(R.string.session_interrupted)
            else -> getString(R.string.session_ready)
        }
        if (message != lastStatus) { status.text = message; lastStatus = message }
        start.isEnabled = enabled
        val label = getString(if (BlePad.sessionOpen) R.string.session_back else R.string.session_start)
        if (start.text != label) start.text = label
        end.visibility = if (BlePad.sessionOpen) View.VISIBLE else View.GONE
        action.visibility = if (!granted || !enabled) View.VISIBLE else View.GONE
        val actionLabel = getString(if (!granted) R.string.bt_grant else R.string.bt_turn_on)
        if (action.text != actionLabel) action.text = actionLabel
        action.setOnClickListener {
            if (!hasPermission() && Build.VERSION.SDK_INT >= 31) {
                val wanted = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE) +
                    if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
                requestPermissions(wanted.toTypedArray(), 1)
            } else {
                try { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                catch (_: SecurityException) { lastStatus = "" }
            }
        }
        findViewById<RadioGroup>(R.id.orientation).let { group ->
            for (i in 0 until group.childCount) group.getChildAt(i).isEnabled = !BlePad.sessionOpen
        }
        // El servidor GATT ya publicó su identidad: cambiarla con él abierto no llegaría a Windows.
        val identityEditable = !BlePad.sessionOpen && BlePad.idle
        identityFields.forEach { it.isEnabled = identityEditable }
        listOf(R.id.identity_slot_a, R.id.identity_slot_b).forEach { findViewById<View>(it).isEnabled = identityEditable }
        findViewById<View>(R.id.identity_save).isEnabled = identityEditable
        findViewById<View>(R.id.identity_reset).isEnabled = identityEditable
        findViewById<View>(R.id.identity_locked).visibility = if (identityEditable) View.GONE else View.VISIBLE
    }
    /** Con targetSdk 35 la app dibuja bajo las barras del sistema y el teclado; el contenido se aparta a mano. */
    private fun applyInsets() {
        val scroll = findViewById<View>(R.id.scroll)
        val root = findViewById<View>(R.id.root)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseBottom = root.paddingBottom
        var bars = listOf(0, 0, 0, 0)
        // En pantallas anchas el texto se limita a una columna legible en lugar de estirarse.
        val maxContent = (560 * resources.displayMetrics.density).toInt()
        fun pad() {
            val (l, t, r, b) = bars
            val extra = ((scroll.width - l - r - maxContent) / 2).coerceAtLeast(0)
            root.setPadding(baseLeft + l + extra, baseTop + t, baseLeft + r + extra, baseBottom + b)
        }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            bars = if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
                listOf(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                listOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            pad()
            insets
        }
        // Las inserciones llegan antes de medir (ancho 0) y en Android 12 no se repiten al girar: el ancho manda.
        scroll.addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) v.post { pad() }
        }
    }

}
