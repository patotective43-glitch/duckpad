package dev.patotective.duckpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Mando táctil con controles recolocables y sticks flotantes. */
class GamepadView(context: Context) : View(context) {

    var onStateChanged: ((PadState) -> Unit)? = null
    var onButtonDown: (() -> Unit)? = null

    var status: String = ""
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }
    var statusIsError = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    private class Button(
        val label: String,
        val mask: Int,
        val color: Int,
        val round: Boolean,
        val face: Boolean = false,
    ) {
        var sub = ""
        val rect = RectF()
        var pointer = NONE
        val pressed get() = pointer != NONE

        fun hit(x: Float, y: Float, slop: Float): Boolean = if (round) {
            hypot(x - rect.centerX(), y - rect.centerY()) <= rect.width() / 2 + slop
        } else {
            x >= rect.left - slop && x <= rect.right + slop && y >= rect.top - slop && y <= rect.bottom + slop
        }
    }

    private class Stick(val clickMask: Int) {
        var homeX = 0f
        var homeY = 0f
        var radius = 0f
        var originX = 0f
        var originY = 0f
        var knobX = 0f
        var knobY = 0f
        var pointer = NONE
        var clicked = false
        var downAt = 0L
        var travel = 0f
        var lastTapEnd = 0L

        val valueX get() = if (pointer == NONE) 0f else ((knobX - originX) / radius).coerceIn(-1f, 1f)
        val valueY get() = if (pointer == NONE) 0f else ((knobY - originY) / radius).coerceIn(-1f, 1f)
    }

    private class DPad {
        var cx = 0f
        var cy = 0f
        var arm = 0f
        var pointer = NONE
        var mask = 0
    }

    private val res = context.resources
    private val face = listOf(
        Button("Y", Buttons.Y, YELLOW, round = true, face = true),
        Button("B", Buttons.B, RED, round = true, face = true),
        Button("A", Buttons.A, GREEN, round = true, face = true),
        Button("X", Buttons.X, BLUE, round = true, face = true),
    )
    private val lt = Button("LT", Buttons.TRIGGER_L, LABEL, round = false)
    private val lb = Button("LB", Buttons.LB, LABEL, round = false)
    private val rb = Button("RB", Buttons.RB, LABEL, round = false)
    private val rt = Button("RT", Buttons.TRIGGER_R, LABEL, round = false)
    private val view = Button(res.getString(R.string.label_view), Buttons.VIEW, LABEL, round = true)
    private val guide = Button(res.getString(R.string.label_guide), Buttons.GUIDE, LABEL, round = true)
    private val menu = Button(res.getString(R.string.label_menu), Buttons.MENU, LABEL, round = true)
    private val buttons by lazy { face + listOf(lt, lb, rb, rt, view, guide, menu, l3, r3) }
    private val facePointers = mutableSetOf<Int>()

    private val leftStick = Stick(Buttons.LEFT_THUMB)
    private val rightStick = Stick(Buttons.RIGHT_THUMB)
    private val dpad = DPad()

    private val layoutPrefs get() = context.getSharedPreferences(if (portraitLayout) "touch_layout_portrait" else "touch_layout", Context.MODE_PRIVATE)
    private var portraitLayout = Prefs(context).portrait
    private val hardwareButtons = mutableMapOf<Int, Int>()
    fun setHardwareButton(key: Int, action: Int, pressed: Boolean) {
        if (pressed) hardwareButtons[key] = action else hardwareButtons.remove(key)
        publish()
        invalidate()
    }
    private fun hardwareMask(): Int = hardwareButtons.values.fold(0) { a, b -> a or b }
    var editing = false
    /** El editor esconde su barra mientras se arrastra un control, para dejar ver lo que hay debajo. */
    var onDragging: ((Boolean) -> Unit)? = null
    private var dragId = NONE
    private var dragTarget = -1
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var positions = mutableMapOf<Int, Pair<Float, Float>>()
    private val l3 = Button("L3", Buttons.LEFT_THUMB, LABEL, round = true)
    private val r3 = Button("R3", Buttons.RIGHT_THUMB, LABEL, round = true)

    private var unit = 1f
    private var slop = 0f
    private var insetLeft = 0
    private var insetRight = 0
    private var insetTop = 0
    private var insetBottom = 0
    private var lastState = PadState.NEUTRAL

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = BOLD
    }
    private val dpadPath = Path()
    private val dpadVertical = Path()
    private val dpadHorizontal = Path()
    private val tmpRect = RectF()

    init {
        isHapticFeedbackEnabled = false
        keepScreenOn = true
        loadPositions()
    }

    private fun loadPositions() {
        positions.clear()
        for (i in 0 until 16) {
            if (layoutPrefs.contains("x$i")) positions[i] = layoutPrefs.getFloat("x$i", .5f) to layoutPrefs.getFloat("y$i", .5f)
        }
    }

    fun reloadLayout() {
        loadPositions()
        if (width > 0 && height > 0) layoutControls(width, height)
    }

    fun saveLayout() {
        val edit = layoutPrefs.edit().clear()
        positions.forEach { (id, p) -> edit.putFloat("x$id", p.first).putFloat("y$id", p.second) }
        edit.apply()
    }

    fun resetLayout() {
        releaseAll()
        positions.clear()
        layoutControls(width, height)
    }

    private fun bounds(id: Int): RectF = when {
        id < buttons.size -> RectF(buttons[id].rect)
        id == buttons.size -> RectF(dpad.cx - dpad.arm, dpad.cy - dpad.arm, dpad.cx + dpad.arm, dpad.cy + dpad.arm)
        else -> (if (id == buttons.size + 1) leftStick else rightStick).let {
            RectF(it.homeX - it.radius, it.homeY - it.radius, it.homeX + it.radius, it.homeY + it.radius)
        }
    }

    private fun position(id: Int, x: Float, y: Float) {
        val r = bounds(id)
        val cx = x.coerceIn(insetLeft + r.width() / 2, max(insetLeft + r.width() / 2, width - insetRight - r.width() / 2))
        val cy = y.coerceIn(insetTop + r.height() / 2, max(insetTop + r.height() / 2, height - insetBottom - r.height() / 2))
        when {
            id < buttons.size -> buttons[id].rect.offsetTo(cx - r.width() / 2, cy - r.height() / 2)
            id == buttons.size -> { dpad.cx = cx; dpad.cy = cy }
            else -> (if (id == buttons.size + 1) leftStick else rightStick).apply { homeX = cx; homeY = cy }
        }
    }

    private fun editTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragId = e.getPointerId(0)
                dragTarget = (0 until buttons.size + 3).firstOrNull { bounds(it).contains(e.x, e.y) } ?: -1
                if (dragTarget >= 0) {
                    val r = bounds(dragTarget)
                    dragOffsetX = e.x - r.centerX()
                    dragOffsetY = e.y - r.centerY()
                    onDragging?.invoke(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(dragId)
                if (dragTarget >= 0 && i >= 0) {
                    position(dragTarget, e.getX(i) - dragOffsetX, e.getY(i) - dragOffsetY)
                    val r = bounds(dragTarget)
                    positions[dragTarget] = ((r.centerX() - insetLeft) / (width - insetLeft - insetRight)) to (r.centerY() / height)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragTarget >= 0) onDragging?.invoke(false)
                dragId = NONE
                dragTarget = -1
            }
        }
        invalidate()
        return true
    }

    fun releaseAll() {
        hardwareButtons.clear()
        facePointers.clear()
        buttons.forEach { it.pointer = NONE }
        listOf(leftStick, rightStick).forEach { it.pointer = NONE; it.clicked = false; it.lastTapEnd = 0L }
        dpad.pointer = NONE
        dpad.mask = 0
        dragId = NONE
        dragTarget = -1
        publish()
        invalidate()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val cutout = insets.displayCutout
        insetLeft = cutout?.safeInsetLeft ?: 0
        insetRight = cutout?.safeInsetRight ?: 0
        insetTop = cutout?.safeInsetTop ?: 0
        insetBottom = cutout?.safeInsetBottom ?: 0
        if (width > 0) layoutControls(width, height)
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0 && portraitLayout != (h > w)) {
            portraitLayout = h > w
            loadPositions()
        }
        layoutControls(w, h)
    }

    /**
     * Medidas en «u» = 1 % del alto, salvo en pantallas poco alargadas (tablets), donde manda el ancho para que los
     * controles no se pisen. Las alturas van en proporción a la pantalla.
     */
    private fun layoutControls(w: Int, h: Int) {
        releaseAll()
        if (h > w) { layoutPortrait(w, h); return }
        val u = min(h / 100f, (w - insetLeft - insetRight) / LANDSCAPE_WIDTH_U)
        unit = u
        slop = 3 * u
        val left = insetLeft + 4 * u
        val right = w - insetRight - 4 * u
        val cx = (insetLeft + w - insetRight) / 2f
        val top = 4 * u
        val barH = max(14 * u, 48 * res.displayMetrics.density)
        val trigW = 24 * u
        val bumpW = 30 * u

        lt.rect.set(left, top, left + trigW, top + barH)
        lb.rect.set(lt.rect.right + 2 * u, top, lt.rect.right + 2 * u + bumpW, top + barH)
        rt.rect.set(right - trigW, top, right, top + barH)
        rb.rect.set(rt.rect.left - 2 * u - bumpW, top, rt.rect.left - 2 * u, top + barH)

        val small = 6.5f * u
        val centerY = .34f * h
        circle(view, cx - 17 * u, centerY, small)
        circle(guide, cx, centerY, small)
        circle(menu, cx + 17 * u, centerY, small)

        val stickR = 17 * u
        leftStick.apply { homeX = left + 24 * u; homeY = .55f * h; radius = stickR }
        rightStick.apply { homeX = cx + 36 * u; homeY = .76f * h; radius = stickR }
        dpad.apply { this.cx = cx - 36 * u; cy = .76f * h; arm = 15 * u }

        val faceX = right - 24 * u
        val faceY = .55f * h
        val gap = 13 * u
        val faceR = 8 * u
        circle(face[0], faceX, faceY - gap, faceR)
        circle(face[1], faceX + gap, faceY, faceR)
        circle(face[2], faceX, faceY + gap, faceR)
        circle(face[3], faceX - gap, faceY, faceR)

        circle(l3, left + 49 * u, .49f * h, small)
        circle(r3, cx + 12 * u, .76f * h, small)
        positions.forEach { (id, p) -> position(id, insetLeft + p.first * (w - insetLeft - insetRight), p.second * h) }
        updateGestureExclusion(w, h)
        invalidate()
    }

    private fun layoutPortrait(w: Int, h: Int) {
        val usableWidth = w - insetLeft - insetRight
        val usableHeight = h - insetTop - insetBottom
        // En tablets el alto no da para el ancho completo: se encoge y se centra.
        val u = min(usableWidth / 100f, usableHeight / PORTRAIT_HEIGHT_U)
        unit = u
        slop = 2 * u
        val left = insetLeft + (usableWidth - 100 * u) / 2
        val usableTop = insetTop.toFloat()
        fun x(v: Float) = left + v * u
        fun y(v: Float) = usableTop + usableHeight * v
        fun bar(b: Button, from: Float, to: Float, row: Float) {
            b.rect.set(x(from), y(row), x(to), y(row) + 14*u)
        }
        bar(lt, 5f, 47f, .09f); bar(rt, 53f, 95f, .09f)
        bar(lb, 5f, 47f, .18f); bar(rb, 53f, 95f, .18f)
        circle(view, x(27f), y(.32f), 7f*u)
        circle(guide, x(50f), y(.32f), 7f*u)
        circle(menu, x(73f), y(.32f), 7f*u)
        leftStick.apply { homeX=x(25f); homeY=y(.51f); radius=19*u }
        rightStick.apply { homeX=x(74f); homeY=y(.81f); radius=19*u }
        dpad.apply { cx=x(25f); cy=y(.81f); arm=17*u }
        val fx=x(74f); val fy=y(.51f); val gap=13*u
        circle(face[0],fx,fy-gap,8*u); circle(face[1],fx+gap,fy,8*u)
        circle(face[2],fx,fy+gap,8*u); circle(face[3],fx-gap,fy,8*u)
        circle(l3,x(25f),y(.66f),7*u); circle(r3,x(74f),y(.66f),7*u)
        positions.forEach { (id,p) -> position(id, insetLeft+p.first*(w-insetLeft-insetRight), p.second*h) }
        updateGestureExclusion(w,h)
        invalidate()
    }

    private fun circle(b: Button, x: Float, y: Float, r: Float) = b.rect.set(x - r, y - r, x + r, y + r)

    /** Evita que el gesto de «atrás» del sistema se dispare al apoyar el pulgar en los bordes. */
    private fun updateGestureExclusion(w: Int, h: Int) {
        if (Build.VERSION.SDK_INT < 29) return
        val edge = (40 * res.displayMetrics.density).roundToInt()
        val tall = min(h, (200 * res.displayMetrics.density).roundToInt())
        systemGestureExclusionRects = listOf(
            Rect(0, h - tall, edge, h),
            Rect(w - edge, h - tall, w, h),
        )
    }

    // Accesibilidad: cada botón, cada dirección de la cruceta y cada stick son un elemento virtual para TalkBack.
    private val a11yCount get() = buttons.size + DPAD_DIRECTIONS.size + 2
    private var a11yFocused = NO_ID
    private var a11yHovered = NO_ID
    private val a11yRect = RectF()

    private fun a11yBounds(id: Int): RectF? {
        val b = buttons.size
        when {
            id < 0 || id >= a11yCount -> return null
            id < b -> a11yRect.set(buttons[id].rect)
            id < b + DPAD_DIRECTIONS.size -> {
                val a = dpad.arm
                val t = a * 0.36f
                when (DPAD_DIRECTIONS[id - b]) {
                    Buttons.DPAD_UP -> a11yRect.set(dpad.cx - t, dpad.cy - a, dpad.cx + t, dpad.cy - t)
                    Buttons.DPAD_DOWN -> a11yRect.set(dpad.cx - t, dpad.cy + t, dpad.cx + t, dpad.cy + a)
                    Buttons.DPAD_LEFT -> a11yRect.set(dpad.cx - a, dpad.cy - t, dpad.cx - t, dpad.cy + t)
                    else -> a11yRect.set(dpad.cx + t, dpad.cy - t, dpad.cx + a, dpad.cy + t)
                }
            }
            else -> (if (id == b + DPAD_DIRECTIONS.size) leftStick else rightStick).let {
                a11yRect.set(it.homeX - it.radius, it.homeY - it.radius, it.homeX + it.radius, it.homeY + it.radius)
            }
        }
        return a11yRect
    }

    private fun a11yLabel(id: Int): String {
        val b = buttons.size
        return when {
            id < b -> buttons[id].label
            id < b + DPAD_DIRECTIONS.size -> res.getString(DPAD_LABELS[id - b])
            id == b + DPAD_DIRECTIONS.size -> res.getString(R.string.a11y_left_stick)
            else -> res.getString(R.string.a11y_right_stick)
        }
    }

    // Los sticks no se pueden pulsar desde TalkBack: necesitan arrastrar el dedo.
    private fun a11yMask(id: Int): Int = when {
        id < buttons.size -> buttons[id].mask
        id < buttons.size + DPAD_DIRECTIONS.size -> DPAD_DIRECTIONS[id - buttons.size]
        else -> 0
    }

    // Primero lo más pequeño: los botones van por encima de las zonas de los sticks.
    private fun a11yAt(x: Float, y: Float): Int = (0 until a11yCount).firstOrNull { a11yBounds(it)?.contains(x, y) == true } ?: NO_ID

    /** Doble toque de TalkBack: el botón se pulsa un momento, como un toque corto con el dedo. */
    private fun a11yPress(id: Int): Boolean {
        val mask = a11yMask(id)
        if (editing || mask == 0) return false
        val key = A11Y_KEY_BASE - id
        setHardwareButton(key, mask, true)
        postDelayed({ setHardwareButton(key, mask, false) }, A11Y_PRESS_MS)
        return true
    }

    private fun sendA11yEvent(id: Int, type: Int) {
        if (!context.getSystemService(AccessibilityManager::class.java).isEnabled) return
        @Suppress("DEPRECATION")
        val event = if (Build.VERSION.SDK_INT >= 30) AccessibilityEvent(type) else AccessibilityEvent.obtain(type)
        event.setSource(this, id)
        event.packageName = context.packageName
        event.className = android.widget.Button::class.java.name
        event.contentDescription = a11yLabel(id)
        parent?.requestSendAccessibilityEvent(this, event)
    }

    private val a11yProvider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
            if (id == HOST_VIEW_ID) {
                @Suppress("DEPRECATION")
                val info = if (Build.VERSION.SDK_INT >= 30) AccessibilityNodeInfo(this@GamepadView) else AccessibilityNodeInfo.obtain(this@GamepadView)
                onInitializeAccessibilityNodeInfo(info)
                for (child in 0 until a11yCount) info.addChild(this@GamepadView, child)
                return info
            }
            val r = a11yBounds(id) ?: return null
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= 30) AccessibilityNodeInfo(this@GamepadView, id) else AccessibilityNodeInfo.obtain(this@GamepadView, id)
            val location = IntArray(2).also { getLocationOnScreen(it) }
            info.setParent(this@GamepadView)
            info.packageName = context.packageName
            info.className = android.widget.Button::class.java.name
            info.contentDescription = a11yLabel(id)
            info.setBoundsInScreen(Rect(location[0] + r.left.toInt(), location[1] + r.top.toInt(), location[0] + r.right.toInt(), location[1] + r.bottom.toInt()))
            info.isEnabled = true
            info.isVisibleToUser = true
            info.isAccessibilityFocused = a11yFocused == id
            info.addAction(
                if (a11yFocused == id) AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                else AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS,
            )
            if (!editing && a11yMask(id) != 0) {
                info.isClickable = true
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            }
            return info
        }

        override fun performAction(id: Int, action: Int, arguments: android.os.Bundle?): Boolean {
            if (id == HOST_VIEW_ID) return performAccessibilityAction(action, arguments)
            return when (action) {
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if (a11yFocused == id) return false
                    if (a11yFocused != NO_ID) sendA11yEvent(a11yFocused, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    a11yFocused = id
                    sendA11yEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (a11yFocused != id) return false
                    a11yFocused = NO_ID
                    sendA11yEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    true
                }
                AccessibilityNodeInfo.ACTION_CLICK -> a11yPress(id).also { if (it) sendA11yEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED) }
                else -> false
            }
        }
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = a11yProvider

    // Con la exploración táctil de TalkBack activa, pasar el dedo por encima anuncia el control que hay debajo.
    override fun dispatchHoverEvent(e: MotionEvent): Boolean {
        if (!context.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled) return super.dispatchHoverEvent(e)
        val id = if (e.actionMasked == MotionEvent.ACTION_HOVER_EXIT) NO_ID else a11yAt(e.x, e.y)
        if (id != a11yHovered) {
            if (id != NO_ID) sendA11yEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
            if (a11yHovered != NO_ID) sendA11yEvent(a11yHovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            a11yHovered = id
        }
        return true
    }

    // Los controles se exponen uno a uno en el proveedor de accesibilidad; performClick no tiene sentido en un mando.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (editing) return editTouch(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> {
                releaseAll()
                return true
            }
        }
        publish()
        invalidate()
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        buttons.firstOrNull { it.pointer == NONE && it.hit(x, y, slop) }?.let {
            it.pointer = id
            if (it.face) facePointers.add(id)
            onButtonDown?.invoke()
            return
        }
        if (dpad.pointer == NONE && abs(x - dpad.cx) <= dpad.arm + slop && abs(y - dpad.cy) <= dpad.arm + slop) {
            dpad.pointer = id
            updateDpad(x, y)
            onButtonDown?.invoke()
            return
        }
        // Sticks flotantes: cualquier toque libre en una mitad mueve el stick de ese lado, centrado donde cae el dedo.
        // Si el editor dejó los dos sticks en el mismo lado, manda el que esté más a la izquierda.
        val cx = (insetLeft + width - insetRight) / 2f
        val (westStick, eastStick) = if (leftStick.homeX <= rightStick.homeX) leftStick to rightStick else rightStick to leftStick
        val stick = if (x < cx) westStick else eastStick
        if (stick.pointer != NONE) return
        val now = SystemClock.uptimeMillis()
        stick.pointer = id
        stick.originX = x
        stick.originY = y
        stick.knobX = x
        stick.knobY = y
        stick.downAt = now
        stick.travel = 0f
        // Doble toque rápido sobre el stick = pulsarlo (L3/R3), y se mantiene mientras el dedo siga apoyado.
        stick.clicked = now - stick.lastTapEnd < DOUBLE_TAP_MS
        if (stick.clicked) onButtonDown?.invoke()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val owner = buttons.firstOrNull { it.pointer == id }
        if (id in facePointers) {
            val target = face.firstOrNull { (it.pointer == NONE || it.pointer == id) && it.hit(x, y, 0f) }
            if (target !== owner) {
                owner?.pointer = NONE
                target?.let { it.pointer = id; onButtonDown?.invoke() }
            }
            return
        }
        if (owner != null) return
        if (dpad.pointer == id) {
            val before = dpad.mask
            updateDpad(x, y)
            if (dpad.mask != before && dpad.mask != 0) onButtonDown?.invoke()
            return
        }
        for (stick in listOf(leftStick, rightStick)) {
            if (stick.pointer != id) continue
            val dx = x - stick.originX
            val dy = y - stick.originY
            val dist = hypot(dx, dy)
            stick.travel = max(stick.travel, dist)
            val k = if (dist > stick.radius) stick.radius / dist else 1f
            stick.knobX = stick.originX + dx * k
            stick.knobY = stick.originY + dy * k
        }
    }

    private fun up(id: Int) {
        facePointers.remove(id)
        buttons.firstOrNull { it.pointer == id }?.let {
            it.pointer = NONE
            return
        }
        if (dpad.pointer == id) {
            dpad.pointer = NONE
            dpad.mask = 0
            return
        }
        for (stick in listOf(leftStick, rightStick)) {
            if (stick.pointer != id) continue
            val now = SystemClock.uptimeMillis()
            val wasTap = now - stick.downAt < TAP_MS && stick.travel < stick.radius * 0.25f && !stick.clicked
            stick.lastTapEnd = if (wasTap) now else 0L
            stick.pointer = NONE
            stick.clicked = false
        }
    }

    private fun updateDpad(x: Float, y: Float) {
        val dx = x - dpad.cx
        val dy = y - dpad.cy
        // Zona muerta pequeña en el centro; fuera de ella, 8 direcciones en sectores de 45°.
        if (hypot(dx, dy) < dpad.arm * 0.25f) {
            dpad.mask = 0
            return
        }
        val deg = Math.toDegrees(atan2(-dy, dx).toDouble()).let { if (it < 0) it + 360 else it }
        val sector = ((deg + 22.5) / 45).toInt() % 8
        dpad.mask = DPAD_SECTORS[sector]
    }

    private fun publish() {
        var mask = dpad.mask or hardwareMask()
        for (b in buttons) if (b.pressed) mask = mask or b.mask
        if (leftStick.clicked) mask = mask or leftStick.clickMask
        if (rightStick.clicked) mask = mask or rightStick.clickMask

        val state = PadState(
            buttons = mask and 0xFFFF,
            lt = if (lt.pressed || mask and Buttons.TRIGGER_L != 0) 255 else 0,
            rt = if (rt.pressed || mask and Buttons.TRIGGER_R != 0) 255 else 0,
            lx = axis(leftStick.valueX),
            ly = axis(-leftStick.valueY),
            rx = axis(rightStick.valueX),
            ry = axis(-rightStick.valueY),
        )
        if (state != lastState) {
            lastState = state
            onStateChanged?.invoke(state)
        }
    }

    private fun axis(v: Float): Int = (v * 32767).roundToInt().coerceIn(-32768, 32767)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BACKGROUND)
        stroke.strokeWidth = max(2f, 0.5f * unit)

        drawStatus(canvas)
        drawStick(canvas, leftStick)
        drawStick(canvas, rightStick)
        drawDpad(canvas)
        for (b in buttons) drawButton(canvas, b)
    }

    private fun drawStatus(canvas: Canvas) {
        if (status.isEmpty()) return
        text.typeface = Typeface.DEFAULT
        text.color = if (statusIsError) WARNING else MUTED
        text.textSize = 4.2f * unit
        // Va en la franja libre entre los gatillos y los botones centrales; si no cabe, se encoge.
        val room = width - insetLeft - insetRight - 8 * unit
        val needed = text.measureText(status)
        if (needed > room) text.textSize *= room / needed
        val cx = (insetLeft + width - insetRight) / 2f
        val cy = if (height > width) insetTop + 8 * unit else 24 * unit
        canvas.drawText(status, cx, cy - (text.descent() + text.ascent()) / 2, text)
        text.typeface = BOLD
    }

    private fun drawButton(canvas: Canvas, b: Button) {
        val on = b.pressed || hardwareMask() and b.mask != 0
        fill.color = if (on) b.color else SURFACE
        stroke.color = if (b.face) b.color else BORDER
        if (b.round) {
            val r = b.rect.width() / 2
            canvas.drawCircle(b.rect.centerX(), b.rect.centerY(), r, fill)
            canvas.drawCircle(b.rect.centerX(), b.rect.centerY(), r, stroke)
        } else {
            val corner = 3 * unit
            canvas.drawRoundRect(b.rect, corner, corner, fill)
            canvas.drawRoundRect(b.rect, corner, corner, stroke)
        }

        text.color = if (on) BACKGROUND else b.color
        val subLabel = b.sub
        if (b.face) {
            text.textSize = b.rect.height() * 0.5f
        } else if (b.round) {
            text.textSize = 3.6f * unit
        } else {
            text.textSize = 5f * unit
        }
        val baseline = if (subLabel.isEmpty()) {
            b.rect.centerY() - (text.descent() + text.ascent()) / 2
        } else {
            b.rect.centerY() - 0.5f * unit
        }
        canvas.drawText(b.label, b.rect.centerX(), baseline, text)
        if (subLabel.isNotEmpty()) {
            text.textSize = 3.4f * unit
            canvas.drawText(subLabel, b.rect.centerX(), baseline + 4.5f * unit, text)
        }
    }

    private fun drawStick(canvas: Canvas, s: Stick) {
        val active = s.pointer != NONE
        val bx = if (active) s.originX else s.homeX
        val by = if (active) s.originY else s.homeY
        fill.color = SURFACE
        stroke.color = BORDER
        canvas.drawCircle(bx, by, s.radius, fill)
        canvas.drawCircle(bx, by, s.radius, stroke)

        val kx = if (active) s.knobX else s.homeX
        val ky = if (active) s.knobY else s.homeY
        fill.color = if (s.clicked) LABEL else KNOB
        canvas.drawCircle(kx, ky, s.radius * 0.5f, fill)
        stroke.color = LABEL
        canvas.drawCircle(kx, ky, s.radius * 0.5f, stroke)

        if (s.clicked) {
            text.color = BACKGROUND
            text.textSize = 4f * unit
            val label = if (s === leftStick) "L3" else "R3"
            canvas.drawText(label, kx, ky - (text.descent() + text.ascent()) / 2, text)
        }
    }

    private fun drawDpad(canvas: Canvas) {
        val a = dpad.arm
        val t = a * 0.36f
        val cx = dpad.cx
        val cy = dpad.cy
        dpadVertical.reset()
        dpadVertical.addRoundRect(cx - t, cy - a, cx + t, cy + a, unit, unit, Path.Direction.CW)
        dpadHorizontal.reset()
        dpadHorizontal.addRoundRect(cx - a, cy - t, cx + a, cy + t, unit, unit, Path.Direction.CW)
        dpadPath.op(dpadVertical, dpadHorizontal, Path.Op.UNION)
        fill.color = SURFACE
        canvas.drawPath(dpadPath, fill)

        fill.color = LABEL
        if (dpad.mask and Buttons.DPAD_UP != 0) drawArm(canvas, cx - t, cy - a, cx + t, cy - t)
        if (dpad.mask and Buttons.DPAD_DOWN != 0) drawArm(canvas, cx - t, cy + t, cx + t, cy + a)
        if (dpad.mask and Buttons.DPAD_LEFT != 0) drawArm(canvas, cx - a, cy - t, cx - t, cy + t)
        if (dpad.mask and Buttons.DPAD_RIGHT != 0) drawArm(canvas, cx + t, cy - t, cx + a, cy + t)

        stroke.color = BORDER
        canvas.drawPath(dpadPath, stroke)
    }

    private fun drawArm(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        tmpRect.set(l, t, r, b)
        canvas.drawRoundRect(tmpRect, unit, unit, fill)
    }

    companion object {
        private const val NO_ID = Int.MIN_VALUE
        // Ancho mínimo (en «u») de la distribución horizontal y alto mínimo de la vertical para que nada se pise.
        private const val LANDSCAPE_WIDTH_U = 205f
        private const val PORTRAIT_HEIGHT_U = 200f
        private const val A11Y_KEY_BASE = -1000
        private const val A11Y_PRESS_MS = 150L
        private val DPAD_DIRECTIONS = intArrayOf(Buttons.DPAD_UP, Buttons.DPAD_DOWN, Buttons.DPAD_LEFT, Buttons.DPAD_RIGHT)
        private val DPAD_LABELS = intArrayOf(R.string.map_up, R.string.map_down, R.string.map_left, R.string.map_right)
        private const val NONE = -1
        private const val DOUBLE_TAP_MS = 300L
        private const val TAP_MS = 220L


        private val BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        private val DPAD_SECTORS = intArrayOf(
            Buttons.DPAD_RIGHT,
            Buttons.DPAD_RIGHT or Buttons.DPAD_UP,
            Buttons.DPAD_UP,
            Buttons.DPAD_UP or Buttons.DPAD_LEFT,
            Buttons.DPAD_LEFT,
            Buttons.DPAD_LEFT or Buttons.DPAD_DOWN,
            Buttons.DPAD_DOWN,
            Buttons.DPAD_DOWN or Buttons.DPAD_RIGHT,
        )

        // Contrastes comprobados con WCAG: etiquetas >= 4.5:1 y bordes >= 3:1 sobre su fondo.
        internal val BACKGROUND = Color.parseColor("#101214")
        internal val SURFACE = Color.parseColor("#1B1E22")
        internal val BORDER = Color.parseColor("#656D76")
        private val KNOB = Color.parseColor("#2A2F35")
        internal val LABEL = Color.parseColor("#C9CED4")
        private val MUTED = Color.parseColor("#9AA0A6")
        private val WARNING = Color.parseColor("#F2B84B")
        internal val GREEN = Color.parseColor("#6CC070")
        // Caramelo claro de la app para la acción principal del editor: 10:1 sobre el fondo del mando.
        internal val CARAMEL = Color.parseColor("#F0B26B")
        private val RED = Color.parseColor("#F0635A")
        private val BLUE = Color.parseColor("#5B9BEA")
        private val YELLOW = Color.parseColor("#F2C744")
    }
}
