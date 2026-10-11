package dev.patotective.duckpad

import android.test.InstrumentationTestCase
import android.view.MotionEvent

@Suppress("DEPRECATION")
class TouchControlsTest : InstrumentationTestCase() {
    private lateinit var layoutsBefore: Map<String, Map<String, *>>
    override fun setUp() {
        super.setUp()
        layoutsBefore = listOf("touch_layout", "touch_layout_portrait").associateWith {
            instrumentation.targetContext.getSharedPreferences(it, 0).all
        }
    }
    override fun tearDown() {
        layoutsBefore.forEach { (name, values) ->
            val restore = instrumentation.targetContext.getSharedPreferences(name, 0).edit().clear()
            values.forEach { (key, value) -> restore.putFloat(key, value as Float) }
            restore.commit()
        }
        super.tearDown()
    }
    private fun checkOnUi(test: () -> Unit) = instrumentation.runOnMainSync(test)
    private fun event(v: GamepadView, action: Int, vararg xy: Pair<Float, Float>) {
        val props = Array(xy.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(xy.size) { i -> MotionEvent.PointerCoords().apply { x = xy[i].first; y = xy[i].second; pressure = 1f; size = 1f } }
        val e = MotionEvent.obtain(1000, 1100, action, xy.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0x1002, 0)
        v.onTouchEvent(e)
        e.recycle()
    }
    private fun view() = GamepadView(instrumentation.targetContext).apply { layout(0, 0, 2400, 1080); resetLayout() }

    fun testHardwareAndTouchReleaseIndependently() = checkOnUi {
        val v = view(); var state = PadState.NEUTRAL
        v.onStateChanged = { state = it }
        v.setHardwareButton(24, Buttons.A, true)
        event(v, MotionEvent.ACTION_DOWN, 2098f to 734f)
        v.setHardwareButton(24, Buttons.A, false)
        assertEquals(Buttons.A, state.buttons)
        event(v, MotionEvent.ACTION_UP, 2098f to 734f)
        assertEquals(PadState.NEUTRAL, state)
        v.setHardwareButton(24, Buttons.A, true)
        v.setHardwareButton(25, Buttons.A, true)
        v.setHardwareButton(24, Buttons.A, false)
        assertEquals(Buttons.A, state.buttons)
        v.setHardwareButton(25, Buttons.A, false)
        assertEquals(PadState.NEUTRAL, state)
        v.setHardwareButton(24, 1 shl 16, true)
        v.setHardwareButton(25, 1 shl 17, true)
        assertEquals(255, state.lt); assertEquals(255, state.rt)
        assertEquals(0, state.buttons)
        v.releaseAll()
        assertEquals(PadState.NEUTRAL, state)
    }

    fun testPortraitTouchAndIndependentLayout() = checkOnUi {
        val portrait = instrumentation.targetContext.getSharedPreferences("touch_layout_portrait", 0)
        val original = portrait.all
        try {
            val v = view(); v.layout(0, 0, 1080, 2400); v.resetLayout()
            var state = PadState.NEUTRAL; v.onStateChanged = { state = it }
            event(v, MotionEvent.ACTION_DOWN, 799f to 1364f)
            assertEquals(Buttons.A, state.buttons)
            event(v, MotionEvent.ACTION_UP, 799f to 1364f)
            v.editing = true
            event(v, MotionEvent.ACTION_DOWN, 799f to 1364f)
            event(v, MotionEvent.ACTION_MOVE, 540f to 2150f)
            event(v, MotionEvent.ACTION_UP, 540f to 2150f)
            v.saveLayout()
            v.editing = false
            v.layout(0, 0, 2400, 1080)
            event(v, MotionEvent.ACTION_DOWN, 2098f to 734f)
            assertEquals(Buttons.A, state.buttons)
            v.releaseAll()
            v.layout(0, 0, 1080, 2400)
            event(v, MotionEvent.ACTION_DOWN, 540f to 2150f)
            assertEquals(Buttons.A, state.buttons)
            v.releaseAll()
        } finally {
            val restore = portrait.edit().clear()
            original.forEach { (k, v) -> restore.putFloat(k, v as Float) }
            restore.commit()
        }
    }

    fun testSimultaneousTriggerFaceAndStickThenCancel() = checkOnUi {
        val v = view(); var state = PadState.NEUTRAL
        v.onStateChanged = { state = it }
        val lt = 173f to 119f; val a = 2098f to 734f; val stick = 302f to 594f
        event(v, MotionEvent.ACTION_DOWN, lt)
        event(v, MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), lt, a)
        event(v, MotionEvent.ACTION_POINTER_DOWN or (2 shl 8), lt, a, stick)
        event(v, MotionEvent.ACTION_MOVE, lt, a, 480f to 420f)
        assertEquals(255, state.lt); assertTrue(state.buttons and Buttons.A != 0)
        assertTrue(state.lx > 15000); assertTrue(state.ly > 15000)
        event(v, MotionEvent.ACTION_POINTER_UP or (1 shl 8), lt, a, stick)
        assertEquals(0, state.buttons and Buttons.A); assertEquals(255, state.lt)
        event(v, MotionEvent.ACTION_CANCEL, lt)
        assertEquals(PadState.NEUTRAL, state)
    }

    fun testDpadDiagonalsAndFaceSlide() = checkOnUi {
        val v = view(); var state = PadState.NEUTRAL; v.onStateChanged = { state = it }
        val cx = 811f; val cy = 821f
        for ((dx, dy) in listOf(-110f to -110f,110f to -110f,110f to 110f,-110f to 110f)) {
            event(v, MotionEvent.ACTION_DOWN, cx+dx to cy+dy)
            val expected = (if (dx < 0) Buttons.DPAD_LEFT else Buttons.DPAD_RIGHT) or (if (dy < 0) Buttons.DPAD_UP else Buttons.DPAD_DOWN)
            assertEquals(expected, state.buttons)
            event(v, MotionEvent.ACTION_UP, cx+dx to cy+dy)
            assertEquals(PadState.NEUTRAL,state)
        }
        event(v, MotionEvent.ACTION_DOWN,2098f to 734f)
        assertEquals(Buttons.A,state.buttons)
        event(v, MotionEvent.ACTION_MOVE,2238f to 594f)
        assertEquals(Buttons.B,state.buttons)
        event(v, MotionEvent.ACTION_MOVE,1200f to 550f)
        assertEquals(0,state.buttons)
        event(v, MotionEvent.ACTION_MOVE,1957f to 594f)
        assertEquals(Buttons.X,state.buttons)
        event(v, MotionEvent.ACTION_CANCEL,1957f to 594f)
        assertEquals(PadState.NEUTRAL,state)
    }

    fun testEditorPersistsAndNeverSendsInput() = checkOnUi {
        val prefs = instrumentation.targetContext.getSharedPreferences("touch_layout",0)
        val original = prefs.all
        try {
            val v = view(); v.editing=true
            var count=0; v.onStateChanged={count++}
            event(v,MotionEvent.ACTION_DOWN,2098f to 734f)
            event(v,MotionEvent.ACTION_MOVE,1200f to 560f)
            event(v,MotionEvent.ACTION_UP,1200f to 560f)
            assertEquals(0,count)
            v.saveLayout()
            val reopened=GamepadView(instrumentation.targetContext); reopened.layout(0,0,2400,1080)
            var state=PadState.NEUTRAL; reopened.onStateChanged={state=it}
            event(reopened,MotionEvent.ACTION_DOWN,1200f to 560f)
            assertEquals(Buttons.A,state.buttons)
            reopened.releaseAll(); assertEquals(PadState.NEUTRAL,state)
            reopened.layout(0,0,2000,900)
            event(reopened,MotionEvent.ACTION_DOWN,1000f to (560f*900/1080))
            assertEquals(Buttons.A,state.buttons)
            reopened.releaseAll()
            reopened.layout(0,0,2400,1080)
            reopened.resetLayout()
            event(reopened,MotionEvent.ACTION_DOWN,2098f to 734f)
            assertEquals(Buttons.A,state.buttons)
        } finally {
            val restore=prefs.edit().clear(); original.forEach { (k,v) -> restore.putFloat(k,v as Float) }; restore.commit()
        }
    }
}
