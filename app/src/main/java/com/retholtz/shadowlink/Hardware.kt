package com.retholtz.shadowlink

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.hid4java.HidDevice
import org.hid4java.HidManager
import org.hid4java.HidServicesListener
import org.hid4java.HidServicesSpecification
import org.hid4java.event.HidServicesEvent
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import kotlin.random.Random

// --- XINPUT JNA BINDINGS ---
interface XInputLibrary : StdCallLibrary {
    companion object {
        val INSTANCE: XInputLibrary? = try {
            Native.load("xinput1_4", XInputLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)
        } catch (e: Exception) {
            try { Native.load("xinput9_1_0", XInputLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS) } catch (e2: Exception) { null }
        }
    }

    @Structure.FieldOrder("wButtons", "bLeftTrigger", "bRightTrigger", "sThumbLX", "sThumbLY", "sThumbRX", "sThumbRY")
    open class XINPUT_GAMEPAD : Structure() {
        @JvmField var wButtons: Short = 0
        @JvmField var bLeftTrigger: Byte = 0
        @JvmField var bRightTrigger: Byte = 0
        @JvmField var sThumbLX: Short = 0
        @JvmField var sThumbLY: Short = 0
        @JvmField var sThumbRX: Short = 0
        @JvmField var sThumbRY: Short = 0
    }

    @Structure.FieldOrder("dwPacketNumber", "Gamepad")
    open class XINPUT_STATE : Structure() {
        @JvmField var dwPacketNumber: Int = 0
        @JvmField var Gamepad: XINPUT_GAMEPAD = XINPUT_GAMEPAD()
    }

    fun XInputGetState(dwUserIndex: Int, pState: XINPUT_STATE): Int
}

// --- CONTROLLER POLLING ---
fun runControllerSniffer() {
    val spec = HidServicesSpecification().apply { scanInterval = controllerScanInterval }
    val hidServices = HidManager.getHidServices(spec)
    val robot = Robot().apply { isAutoWaitForIdle = false }

    // Start Dedicated XInput Thread for Standard Inputs (Triggers, Bumpers, Face, D-Pad)
    Thread {
        val xState = XInputLibrary.XINPUT_STATE()
        val standardStates = Array(14) { ButtonState() }

        while (true) {
            val xInput = XInputLibrary.INSTANCE
            if (xInput != null) {
                var wButtons = 0
                var ltVal = 0
                var rtVal = 0
                var anyConnected = false

                // Scan all 4 Xbox Player slots and merge the inputs.
                for (i in 0..3) {
                    val res = xInput.XInputGetState(i, xState)
                    if (res == 0) { // SUCCESS
                        anyConnected = true
                        wButtons = wButtons or (xState.Gamepad.wButtons.toInt() and 0xFFFF)
                        val lt = xState.Gamepad.bLeftTrigger.toInt() and 0xFF
                        val rt = xState.Gamepad.bRightTrigger.toInt() and 0xFF
                        if (lt > ltVal) ltVal = lt
                        if (rt > rtVal) rtVal = rt
                    }
                }

                if (anyConnected) {
                    val sLb = (wButtons and 0x0100) != 0
                    val sRb = (wButtons and 0x0200) != 0
                    val sLt = ltVal > 128
                    val sRt = rtVal > 128
                    val sA = (wButtons and 0x1000) != 0
                    val sB = (wButtons and 0x2000) != 0
                    val sX = (wButtons and 0x4000) != 0
                    val sY = (wButtons and 0x8000) != 0
                    val sL3 = (wButtons and 0x0040) != 0
                    val sR3 = (wButtons and 0x0080) != 0
                    val sDUp = (wButtons and 0x0001) != 0
                    val sDDown = (wButtons and 0x0002) != 0
                    val sDLeft = (wButtons and 0x0004) != 0
                    val sDRight = (wButtons and 0x0008) != 0

                    val cl = activeProfile.layers[activeLayer - 1]

                    fun checkStdState(s: Boolean, bs: ButtonState, bind: PaddleBind) {
                        if (s && !bs.pressed) {
                            bs.pressed = true
                            bs.singleActionFired = false
                            if (bind.enabled) {
                                bs.singleActionFired = true
                                bs.fire(robot, bind)
                            }
                        } else if (!s && bs.pressed) {
                            bs.cancelPending()
                            if (bind.enabled && !bs.singleActionFired) bs.fire(robot, bind)
                            bs.releaseIfActive(robot)
                            bs.pressed = false
                        }
                    }

                    checkStdState(sLb, standardStates[0], cl.lb)
                    checkStdState(sRb, standardStates[1], cl.rb)
                    checkStdState(sLt, standardStates[2], cl.lt)
                    checkStdState(sRt, standardStates[3], cl.rt)
                    checkStdState(sA, standardStates[4], cl.a)
                    checkStdState(sB, standardStates[5], cl.b)
                    checkStdState(sX, standardStates[6], cl.x)
                    checkStdState(sY, standardStates[7], cl.y)
                    checkStdState(sL3, standardStates[8], cl.l3)
                    checkStdState(sR3, standardStates[9], cl.r3)
                    checkStdState(sDUp, standardStates[10], cl.dUp)
                    checkStdState(sDDown, standardStates[11], cl.dDown)
                    checkStdState(sDLeft, standardStates[12], cl.dLeft)
                    checkStdState(sDRight, standardStates[13], cl.dRight)
                } else {
                    // Clear states if no XInput controllers are connected
                    standardStates.forEach { bs ->
                        if (bs.pressed) { bs.cancelPending(); bs.releaseIfActive(robot); bs.pressed = false }
                    }
                }
            } else {
                standardStates.forEach { bs -> if (bs.pressed) { bs.cancelPending(); bs.releaseIfActive(robot); bs.pressed = false } }
            }
            Thread.sleep(10)
        }
    }.start()

    val controllerListener = object : HidServicesListener {
        @Volatile var isConnected = false
        var readingThread: Thread? = null

        override fun hidDeviceAttached(event: HidServicesEvent) {
            val device = event.hidDevice
            if (device.vendorId == 0x0B05 && Integer.toHexString(device.usagePage).endsWith("c3")) {
                startReading(device)
            }
        }

        override fun hidDeviceDetached(event: HidServicesEvent) {
            val device = event.hidDevice
            if (device.vendorId == 0x0B05 && Integer.toHexString(device.usagePage).endsWith("c3")) {
                isConnected = false
                readingThread?.interrupt()
            }
        }

        override fun hidFailure(event: HidServicesEvent) {}

        @Synchronized
        fun startReading(raikiri: HidDevice) {
            if (isConnected) return
            if (raikiri.open()) {
                isConnected = true

                // Pause USB bus polling while connected to prevent crashing other peripherals
                hidServices.stop()

                readingThread = Thread {
                    val actionTimer = java.util.Timer("ShadowLink-ActionTimer", true)

                    val m1State = ButtonState(); val m2State = ButtonState()
                    val m3State = ButtonState(); val m4State = ButtonState()
                    val cmdState = ButtonState(); val libState = ButtonState()

                    val m1m2State = ButtonState(); val m1m3State = ButtonState()
                    val m1m4State = ButtonState(); val m2m3State = ButtonState()
                    val m2m4State = ButtonState(); val m3m4State = ButtonState()

                    var wasToggleTriggered = false

                    // Hardware Debounce State Variables
                    var lastM1Time = 0L; var lastM2Time = 0L
                    var lastM3Time = 0L; var lastM4Time = 0L
                    var lastCmdTime = 0L; var lastLibTime = 0L

                    var debouncedS1 = false; var debouncedS2 = false
                    var debouncedS3 = false; var debouncedS4 = false
                    var debouncedSCmd = false; var debouncedSLib = false

                    try {
                        while (isConnected) {
                            try {
                                val data = ByteArray(64)
                                val read = raikiri.read(data, 500)

                                if (read > 0 && (data[0].toInt() and 0xFF) == 0xB3) {
                                    val p = activeProfile
                                    val isAltMode = data[3].toInt() == 2

                                    val s1 = !isAltMode && data[8].toInt() == 1
                                    val s2 = !isAltMode && data[6].toInt() == 1
                                    val s3 = !isAltMode && data[5].toInt() == 1
                                    val s4 = !isAltMode && data[7].toInt() == 1

                                    val sCmd = isAltMode && data[5].toInt() == 1
                                    val sLib = isAltMode && data[6].toInt() == 1

                                    // Apply Industrial Debounce Filter (25ms window)
                                    val now = System.currentTimeMillis()
                                    val debounceMs = 25L

                                    if (s1 != debouncedS1) {
                                        if (now - lastM1Time > debounceMs) { debouncedS1 = s1; lastM1Time = now }
                                    } else { lastM1Time = now }

                                    if (s2 != debouncedS2) {
                                        if (now - lastM2Time > debounceMs) { debouncedS2 = s2; lastM2Time = now }
                                    } else { lastM2Time = now }

                                    if (s3 != debouncedS3) {
                                        if (now - lastM3Time > debounceMs) { debouncedS3 = s3; lastM3Time = now }
                                    } else { lastM3Time = now }

                                    if (s4 != debouncedS4) {
                                        if (now - lastM4Time > debounceMs) { debouncedS4 = s4; lastM4Time = now }
                                    } else { lastM4Time = now }

                                    if (sCmd != debouncedSCmd) {
                                        if (now - lastCmdTime > debounceMs) { debouncedSCmd = sCmd; lastCmdTime = now }
                                    } else { lastCmdTime = now }

                                    if (sLib != debouncedSLib) {
                                        if (now - lastLibTime > debounceMs) { debouncedSLib = sLib; lastLibTime = now }
                                    } else { lastLibTime = now }

                                    val states = mapOf(
                                        "M1" to debouncedS1,
                                        "M2" to debouncedS2,
                                        "M3" to debouncedS3,
                                        "M4" to debouncedS4,
                                        "Command" to debouncedSCmd,
                                        "Library" to debouncedSLib
                                    )

                                    val t1 = p.toggleButton1
                                    val t2 = p.toggleButton2

                                    val t1Pressed = if (t1 != "None") states[t1] == true else false
                                    val t2Pressed = if (t2 != "None") states[t2] == true else false

                                    val isSingleToggle = (t1 != "None" && t2 == "None") || (t1 == "None" && t2 != "None") || (t1 != "None" && t1 == t2)
                                    val singleToggleBtn = if (t1 != "None") t1 else t2

                                    val isComboTriggered = if (!isSingleToggle && t1 != "None" && t2 != "None") {
                                        t1Pressed && t2Pressed
                                    } else if (isSingleToggle) {
                                        if (t1 != "None") t1Pressed else t2Pressed
                                    } else {
                                        false
                                    }

                                    val consumed = mutableSetOf<String>()

                                    if (isComboTriggered && !wasToggleTriggered) {
                                        wasToggleTriggered = true

                                        m1State.cancelAndRelease(robot); m2State.cancelAndRelease(robot)
                                        m3State.cancelAndRelease(robot); m4State.cancelAndRelease(robot)
                                        cmdState.cancelAndRelease(robot); libState.cancelAndRelease(robot)

                                        m1m2State.cancelAndRelease(robot); m1m3State.cancelAndRelease(robot)
                                        m1m4State.cancelAndRelease(robot); m2m3State.cancelAndRelease(robot)
                                        m2m4State.cancelAndRelease(robot); m3m4State.cancelAndRelease(robot)

                                        val availableLayers = p.layers.mapIndexedNotNull { index, layer -> if (layer.enabled) index + 1 else null }
                                        if (availableLayers.isNotEmpty()) {
                                            val currentIndex = availableLayers.indexOf(activeLayer)
                                            activeLayer = if (currentIndex != -1 && currentIndex + 1 < availableLayers.size) {
                                                availableLayers[currentIndex + 1]
                                            } else {
                                                availableLayers[0]
                                            }
                                            showOSD(p.layers[activeLayer - 1].name)
                                        }
                                    } else if (!isComboTriggered) {
                                        wasToggleTriggered = false
                                    }

                                    if (isComboTriggered) {
                                        if (t1 != "None") consumed.add(t1)
                                        if (t2 != "None") consumed.add(t2)
                                    } else if (isSingleToggle && singleToggleBtn != "None") {
                                        consumed.add(singleToggleBtn)
                                    }

                                    val currentLayerConfig = p.layers[activeLayer - 1]

                                    fun handleCombo(name1: String, sA: Boolean, name2: String, sB: Boolean, bs: ButtonState, bind: PaddleBind, stateA: ButtonState, stateB: ButtonState) {
                                        if (sA && sB && bind.enabled && !consumed.contains(name1) && !consumed.contains(name2)) {
                                            consumed.add(name1); consumed.add(name2)
                                            if (!bs.pressed) {
                                                stateA.cancelPending(); stateB.cancelPending()
                                                if (stateA.singleActionFired) stateA.releaseIfActive(robot)
                                                if (stateB.singleActionFired) stateB.releaseIfActive(robot)
                                                stateA.comboConsumed = true; stateB.comboConsumed = true

                                                bs.pressed = true; bs.singleActionFired = true
                                                bs.fire(robot, bind)
                                            }
                                        } else {
                                            if (bs.pressed) { bs.releaseIfActive(robot); bs.pressed = false }
                                        }
                                    }

                                    handleCombo("M1", debouncedS1, "M2", debouncedS2, m1m2State, currentLayerConfig.m1_m2, m1State, m2State)
                                    handleCombo("M1", debouncedS1, "M3", debouncedS3, m1m3State, currentLayerConfig.m1_m3, m1State, m3State)
                                    handleCombo("M1", debouncedS1, "M4", debouncedS4, m1m4State, currentLayerConfig.m1_m4, m1State, m4State)
                                    handleCombo("M2", debouncedS2, "M3", debouncedS3, m2m3State, currentLayerConfig.m2_m3, m2State, m3State)
                                    handleCombo("M2", debouncedS2, "M4", debouncedS4, m2m4State, currentLayerConfig.m2_m4, m2State, m4State)
                                    handleCombo("M3", debouncedS3, "M4", debouncedS4, m3m4State, currentLayerConfig.m3_m4, m3State, m4State)

                                    fun handleSingle(name: String, state: Boolean, bs: ButtonState, bind: PaddleBind) {
                                        if (consumed.contains(name)) {
                                            if (!bs.pressed) bs.pressed = true
                                            bs.cancelPending()
                                            if (bs.singleActionFired) { bs.releaseIfActive(robot); bs.singleActionFired = false }
                                            bs.comboConsumed = true
                                            return
                                        }

                                        if (state && !bs.pressed) {
                                            bs.pressed = true; bs.singleActionFired = false; bs.comboConsumed = false
                                            if (bind.enabled) {
                                                if (p.comboBufferMs > 0) {
                                                    bs.pendingTask = object : java.util.TimerTask() {
                                                        override fun run() {
                                                            if (!bs.comboConsumed) {
                                                                bs.singleActionFired = true
                                                                bs.fire(robot, bind)
                                                            }
                                                        }
                                                    }
                                                    actionTimer.schedule(bs.pendingTask, p.comboBufferMs.toLong())
                                                } else {
                                                    bs.singleActionFired = true
                                                    bs.fire(robot, bind)
                                                }
                                            }
                                        } else if (!state && bs.pressed) {
                                            bs.cancelPending()
                                            if (bind.enabled && !bs.singleActionFired && !bs.comboConsumed) bs.fire(robot, bind)
                                            bs.releaseIfActive(robot); bs.pressed = false; bs.comboConsumed = false
                                        }
                                    }

                                    handleSingle("M1", debouncedS1, m1State, currentLayerConfig.m1)
                                    handleSingle("M2", debouncedS2, m2State, currentLayerConfig.m2)
                                    handleSingle("M3", debouncedS3, m3State, currentLayerConfig.m3)
                                    handleSingle("M4", debouncedS4, m4State, currentLayerConfig.m4)
                                    handleSingle("Command", debouncedSCmd, cmdState, currentLayerConfig.cmd)
                                    handleSingle("Library", debouncedSLib, libState, currentLayerConfig.lib)

                                } else if (read < 0) {
                                    isConnected = false
                                    raikiri.close()
                                }
                            } catch (e: Exception) {
                                isConnected = false
                                raikiri.close()
                            }
                        }
                    } finally {
                        actionTimer.cancel()

                        // Resume USB polling only if the controller actually disconnected
                        if (!isConnected) {
                            hidServices.start()
                        }
                    }
                }
                readingThread?.start()
            }
        }
    }

    hidServices.addHidServicesListener(controllerListener)
    hidServices.start()

    Thread {
        while (true) {
            if (!controllerListener.isConnected) {
                try {
                    hidServices.attachedHidDevices
                        .filter { it.vendorId == 0x0B05 }
                        .find { Integer.toHexString(it.usagePage).endsWith("c3") }
                        ?.let { controllerListener.startReading(it) }
                } catch (e: Exception) {}
            }
            Thread.sleep(2000)
        }
    }.start()
}

// --- INPUT INJECTION LOGIC (THREAD-SAFE WRAPPERS) ---

fun pressKeyBind(robot: Robot, b: PaddleBind) {
    synchronized(robot) {
        try {
            if (b.keyChar.startsWith("Xbox_")) return

            if (b.win) robot.keyPress(KeyEvent.VK_WINDOWS)
            if (b.shift) robot.keyPress(KeyEvent.VK_SHIFT)
            if (b.ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
            if (b.alt) robot.keyPress(KeyEvent.VK_ALT)

            val mouseMask = getMouseMask(b.keyChar)
            if (mouseMask != 0) {
                robot.mousePress(mouseMask)
            } else {
                val k = getKeyCode(b.keyChar)
                if (k != null) robot.keyPress(k)
            }
        } catch (e: Exception) {}
    }
}

fun releaseKeyBind(robot: Robot, b: PaddleBind) {
    synchronized(robot) {
        try {
            if (b.keyChar.startsWith("Xbox_")) return

            val mouseMask = getMouseMask(b.keyChar)
            if (mouseMask != 0) {
                robot.mouseRelease(mouseMask)
            } else {
                val k = getKeyCode(b.keyChar)
                if (k != null) robot.keyRelease(k)
            }

            if (b.alt) robot.keyRelease(KeyEvent.VK_ALT)
            if (b.ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
            if (b.shift) robot.keyRelease(KeyEvent.VK_SHIFT)
            if (b.win) robot.keyRelease(KeyEvent.VK_WINDOWS)
        } catch (e: Exception) {}
    }
}

// --- MACRO PARSING UTILITY ---
fun parseMacroText(macroText: String): List<String> {
    val result = mutableListOf<String>()
    // Split by common sequence delimiters: commas and semicolons
    val rawTokens = macroText.split(Regex("[,;]")).map { it.trim() }
    for (rawToken in rawTokens) {
        if (rawToken.isEmpty()) continue
        // Matches a delay number followed by an action separated by period(s) or space(s) e.g., "350. MClick" or "350 MClick"
        val match = Regex("^(\\d+)(?:\\s*\\.\\s*|\\s+)([a-zA-Z_].*)$").matchEntire(rawToken)
        if (match != null) {
            result.add(match.groupValues[1]) // Delay (e.g. 350)
            result.add(match.groupValues[2]) // Action (e.g. MClick)
        } else {
            result.add(rawToken)
        }
    }
    return result
}

fun processMacroToken(robot: Robot, token: String, pressedKeys: MutableSet<Int>, pressedMouse: MutableSet<Int>) {
    val t = token.trim()
    if (t.isEmpty() || Thread.currentThread().isInterrupted) return

    // Dynamic delay
    if (t.contains("~")) {
        val parts = t.split("~")
        if (parts.size == 2) {
            val min = parts[0].trim().toLongOrNull()
            val max = parts[1].trim().toLongOrNull()
            if (min != null && max != null && min <= max) {
                val delay = Random.nextLong(min, max + 1)
                if (delay > 0) Thread.sleep(delay)
                return
            }
        }
    }

    // Static delay
    val d = t.toLongOrNull()
    if (d != null) {
        if (d > 0) Thread.sleep(d)
        return
    }

    val p = t.split(Regex("\\s+"))

    if (p[0].equals("MouseAbs", ignoreCase = true) && p.size >= 3) {
        val x = p[1].toIntOrNull() ?: return
        val y = p[2].toIntOrNull() ?: return
        try {
            synchronized(robot) { robot.mouseMove(x, y) }
        } catch (e: Exception) {}
        return
    }

    if (p[0].equals("MouseDelta", ignoreCase = true) && p.size >= 3) {
        val dx = p[1].toIntOrNull() ?: return
        val dy = p[2].toIntOrNull() ?: return
        val currentPos = MouseInfo.getPointerInfo().location
        try {
            synchronized(robot) { robot.mouseMove(currentPos.x + dx, currentPos.y + dy) }
        } catch (e: Exception) {}
        return
    }

    val tLower = t.lowercase()
    val act: String
    val keyStr: String

    if (tLower.endsWith(" down")) {
        act = "down"
        keyStr = t.substring(0, t.length - 5).trim()
    } else if (tLower.endsWith(" up")) {
        act = "up"
        keyStr = t.substring(0, t.length - 3).trim()
    } else {
        act = "tap"
        keyStr = t
    }

    val key = keyStr
    if (key.startsWith("Xbox_", ignoreCase = true)) return

    val mouseMask = getMouseMask(key)

    if (mouseMask != 0) {
        try {
            when (act) {
                "down" -> {
                    synchronized(robot) { robot.mousePress(mouseMask) }
                    pressedMouse.add(mouseMask)
                }
                "up" -> {
                    synchronized(robot) { robot.mouseRelease(mouseMask) }
                    pressedMouse.remove(mouseMask)
                }
                else -> { // TAP
                    synchronized(robot) { robot.mousePress(mouseMask) }
                    pressedMouse.add(mouseMask)
                    Thread.sleep(50)
                    synchronized(robot) { robot.mouseRelease(mouseMask) }
                    pressedMouse.remove(mouseMask)
                }
            }
        } catch (e: Exception) {}
    } else {
        val k = getKeyCode(key) ?: return
        try {
            when (act) {
                "down" -> {
                    synchronized(robot) { robot.keyPress(k) }
                    pressedKeys.add(k)
                }
                "up" -> {
                    synchronized(robot) { robot.keyRelease(k) }
                    pressedKeys.remove(k)
                }
                else -> { // TAP
                    synchronized(robot) { robot.keyPress(k) }
                    pressedKeys.add(k)
                    Thread.sleep(50)
                    synchronized(robot) { robot.keyRelease(k) }
                    pressedKeys.remove(k)
                }
            }
        } catch (e: Exception) {}
    }
}

fun executeMacro(robot: Robot, b: PaddleBind, state: ButtonState): Thread {
    val t = Thread {
        val pressedKeys = mutableSetOf<Int>()
        val pressedMouse = mutableSetOf<Int>()
        try {
            do {
                val tokens = parseMacroText(b.macroText)
                for (token in tokens) {
                    if (Thread.currentThread().isInterrupted) break
                    processMacroToken(robot, token, pressedKeys, pressedMouse)
                }
            } while (b.repeatMacro && state.activeBind === b && !Thread.currentThread().isInterrupted)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            // Keep executing or log silently
        } finally {
            // Clean up left-over pressed physical keys safely on release/interruption
            pressedKeys.forEach { k ->
                synchronized(robot) {
                    try { robot.keyRelease(k) } catch (e: Exception) {}
                }
            }
            pressedMouse.forEach { m ->
                synchronized(robot) {
                    try { robot.mouseRelease(m) } catch (e: Exception) {}
                }
            }
        }
    }
    t.start()
    return t
}

fun executeMacroStep(robot: Robot, b: PaddleBind, state: ButtonState) {
    val tokens = parseMacroText(b.macroText).filter { s: String ->
        s.isNotEmpty() && !s.contains("~") && s.toLongOrNull() == null
    }

    if (tokens.isEmpty()) return

    Thread {
        // Synchronize on the state itself to avoid concurrent indexing anomalies
        synchronized(state) {
            try {
                val token = tokens[state.stepIndex % tokens.size]
                val pressedKeys = mutableSetOf<Int>()
                val pressedMouse = mutableSetOf<Int>()

                processMacroToken(robot, token, pressedKeys, pressedMouse)

                state.stepIndex = (state.stepIndex + 1) % tokens.size
            } catch (e: Exception) {}
        }
    }.start()
}