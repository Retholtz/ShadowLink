package com.retholtz.shadowlink

import org.hid4java.HidManager
import java.awt.Robot
import java.awt.event.KeyEvent
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import javax.swing.*
import java.awt.GridLayout
import java.awt.FlowLayout
import java.awt.Font

// Data class to hold the configuration for a single paddle
data class PaddleBind(
    var keyChar: String,
    var shift: Boolean,
    var ctrl: Boolean,
    var alt: Boolean
)

// Global memory for our bindings
val m1Bind = PaddleBind("J", shift = false, ctrl = false, alt = false)
val m2Bind = PaddleBind("L", shift = false, ctrl = false, alt = false)
val m3Bind = PaddleBind("G", shift = false, ctrl = false, alt = false)
val m4Bind = PaddleBind("M", shift = false, ctrl = false, alt = false)

val configFile = File("config.properties")

// ------------------------------------------------------------------------
// --- COMPREHENSIVE KEY MAPPING ---
// ------------------------------------------------------------------------
val SUPPORTED_KEYS = arrayOf(
    // Letters
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
    // Numbers
    "`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "Backspace",
    // Function Keys
    "ESC", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
    // Special / Navigation Keys
    "Insert", "Home", "Page Up", "Delete", "End", "Page Down",
    "Space", "Enter", "Tab",
    // Arrows
    "Up", "Down", "Left", "Right",
    // Punctuation & Symbols
    ",", ".", "/", "\\",
    ";", "'", "[", "]"
)

val KEY_MAP = mapOf(
    "A" to KeyEvent.VK_A, "B" to KeyEvent.VK_B, "C" to KeyEvent.VK_C, "D" to KeyEvent.VK_D, "E" to KeyEvent.VK_E,
    "F" to KeyEvent.VK_F, "G" to KeyEvent.VK_G, "H" to KeyEvent.VK_H, "I" to KeyEvent.VK_I, "J" to KeyEvent.VK_J,
    "K" to KeyEvent.VK_K, "L" to KeyEvent.VK_L, "M" to KeyEvent.VK_M, "N" to KeyEvent.VK_N, "O" to KeyEvent.VK_O,
    "P" to KeyEvent.VK_P, "Q" to KeyEvent.VK_Q, "R" to KeyEvent.VK_R, "S" to KeyEvent.VK_S, "T" to KeyEvent.VK_T,
    "U" to KeyEvent.VK_U, "V" to KeyEvent.VK_V, "W" to KeyEvent.VK_W, "X" to KeyEvent.VK_X, "Y" to KeyEvent.VK_Y, "Z" to KeyEvent.VK_Z,

    "0" to KeyEvent.VK_0, "1" to KeyEvent.VK_1, "2" to KeyEvent.VK_2, "3" to KeyEvent.VK_3, "4" to KeyEvent.VK_4,
    "5" to KeyEvent.VK_5, "6" to KeyEvent.VK_6, "7" to KeyEvent.VK_7, "8" to KeyEvent.VK_8, "9" to KeyEvent.VK_9,

    "F1" to KeyEvent.VK_F1, "F2" to KeyEvent.VK_F2, "F3" to KeyEvent.VK_F3, "F4" to KeyEvent.VK_F4,
    "F5" to KeyEvent.VK_F5, "F6" to KeyEvent.VK_F6, "F7" to KeyEvent.VK_F7, "F8" to KeyEvent.VK_F8,
    "F9" to KeyEvent.VK_F9, "F10" to KeyEvent.VK_F10, "F11" to KeyEvent.VK_F11, "F12" to KeyEvent.VK_F12,

    "Insert" to KeyEvent.VK_INSERT, "Delete" to KeyEvent.VK_DELETE, "Home" to KeyEvent.VK_HOME, "End" to KeyEvent.VK_END,
    "Page Up" to KeyEvent.VK_PAGE_UP, "Page Down" to KeyEvent.VK_PAGE_DOWN,
    "Space" to KeyEvent.VK_SPACE, "Enter" to KeyEvent.VK_ENTER, "Tab" to KeyEvent.VK_TAB, "ESC" to KeyEvent.VK_ESCAPE, "Backspace" to KeyEvent.VK_BACK_SPACE,
    "Up" to KeyEvent.VK_UP, "DOWN" to KeyEvent.VK_DOWN, "Left" to KeyEvent.VK_LEFT, "Right" to KeyEvent.VK_RIGHT,

    "-" to KeyEvent.VK_MINUS, "=" to KeyEvent.VK_EQUALS, "," to KeyEvent.VK_COMMA, "." to KeyEvent.VK_PERIOD,
    "/" to KeyEvent.VK_SLASH, "\\" to KeyEvent.VK_BACK_SLASH, ";" to KeyEvent.VK_SEMICOLON,
    "'" to KeyEvent.VK_QUOTE, "[" to KeyEvent.VK_OPEN_BRACKET, "]" to KeyEvent.VK_CLOSE_BRACKET,
    "`" to KeyEvent.VK_BACK_QUOTE
)

fun main() {
    loadConfig()

    SwingUtilities.invokeLater {
        createAndShowGUI()
    }

    Thread {
        runControllerSniffer()
    }.start()
}

/**
 * Creates the lightweight Swing UI window.
 */
fun createAndShowGUI() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        val baseFont = Font("Segoe UI", Font.PLAIN, 14)
        UIManager.put("Label.font", baseFont)
        UIManager.put("CheckBox.font", baseFont)
        UIManager.put("ComboBox.font", baseFont)
        UIManager.put("Button.font", baseFont)
    } catch (e: Exception) {
        // Ignore
    }

    val frame = JFrame("ShadowLink - ROG Raikiri II")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(620, 350)
    frame.setLocationRelativeTo(null)
    frame.layout = GridLayout(6, 1, 5, 5)

    val headerLabel = JLabel("Configure Back Paddles", SwingConstants.CENTER)
    headerLabel.font = Font("Segoe UI", Font.BOLD, 18)
    frame.add(headerLabel)

    val m1Controls = createPaddleRow("M1 (Bottom Left)", m1Bind)
    val m2Controls = createPaddleRow("M2 (Top Left)", m2Bind)
    val m3Controls = createPaddleRow("M3 (Top Right)", m3Bind)
    val m4Controls = createPaddleRow("M4 (Bottom Right)", m4Bind)

    frame.add(m1Controls.panel)
    frame.add(m2Controls.panel)
    frame.add(m3Controls.panel)
    frame.add(m4Controls.panel)

    val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
    val saveButton = JButton("Save & Apply")
    saveButton.addActionListener {
        updateBindFromUI(m1Bind, m1Controls)
        updateBindFromUI(m2Bind, m2Controls)
        updateBindFromUI(m3Bind, m3Controls)
        updateBindFromUI(m4Bind, m4Controls)

        saveConfig()
        JOptionPane.showMessageDialog(frame, "Bindings Saved and Applied instantly!", "Success", JOptionPane.INFORMATION_MESSAGE)
    }
    buttonPanel.add(saveButton)
    frame.add(buttonPanel)

    frame.isVisible = true
}

class PaddleUIControls(
    val panel: JPanel,
    val shiftBox: JCheckBox,
    val ctrlBox: JCheckBox,
    val altBox: JCheckBox,
    val keyDropdown: JComboBox<String>
)

fun createPaddleRow(name: String, bind: PaddleBind): PaddleUIControls {
    val panel = JPanel(FlowLayout(FlowLayout.CENTER))
    panel.add(JLabel("$name: "))

    val shiftBox = JCheckBox("Shift", bind.shift)
    val ctrlBox = JCheckBox("Ctrl", bind.ctrl)
    val altBox = JCheckBox("Alt", bind.alt)

    val keyDropdown = JComboBox(SUPPORTED_KEYS)
    val savedKey = bind.keyChar
    val match = SUPPORTED_KEYS.firstOrNull { it.equals(savedKey, ignoreCase = true) }
    if (match != null) {
        keyDropdown.selectedItem = match
    } else {
        keyDropdown.selectedItem = "A"
    }

    panel.add(shiftBox)
    panel.add(ctrlBox)
    panel.add(altBox)
    panel.add(JLabel(" + "))
    panel.add(keyDropdown)

    return PaddleUIControls(panel, shiftBox, ctrlBox, altBox, keyDropdown)
}

fun updateBindFromUI(bind: PaddleBind, controls: PaddleUIControls) {
    bind.shift = controls.shiftBox.isSelected
    bind.ctrl = controls.ctrlBox.isSelected
    bind.alt = controls.altBox.isSelected

    bind.keyChar = controls.keyDropdown.selectedItem?.toString() ?: "A"
}

/**
 * Configuration File Management
 */
fun loadConfig() {
    val props = Properties()
    if (configFile.exists()) {
        FileInputStream(configFile).use { input -> props.load(input) }

        m1Bind.keyChar = props.getProperty("M1_KEY", "J")
        m1Bind.shift = props.getProperty("M1_SHIFT", "false").toBoolean()
        m1Bind.ctrl = props.getProperty("M1_CTRL", "false").toBoolean()
        m1Bind.alt = props.getProperty("M1_ALT", "false").toBoolean()

        m2Bind.keyChar = props.getProperty("M2_KEY", "L")
        m2Bind.shift = props.getProperty("M2_SHIFT", "false").toBoolean()
        m2Bind.ctrl = props.getProperty("M2_CTRL", "false").toBoolean()
        m2Bind.alt = props.getProperty("M2_ALT", "false").toBoolean()

        m3Bind.keyChar = props.getProperty("M3_KEY", "G")
        m3Bind.shift = props.getProperty("M3_SHIFT", "false").toBoolean()
        m3Bind.ctrl = props.getProperty("M3_CTRL", "false").toBoolean()
        m3Bind.alt = props.getProperty("M3_ALT", "false").toBoolean()

        m4Bind.keyChar = props.getProperty("M4_KEY", "M")
        m4Bind.shift = props.getProperty("M4_SHIFT", "false").toBoolean()
        m4Bind.ctrl = props.getProperty("M4_CTRL", "false").toBoolean()
        m4Bind.alt = props.getProperty("M4_ALT", "false").toBoolean()
    }
}

fun saveConfig() {
    val props = Properties()

    props.setProperty("M1_KEY", m1Bind.keyChar)
    props.setProperty("M1_SHIFT", m1Bind.shift.toString())
    props.setProperty("M1_CTRL", m1Bind.ctrl.toString())
    props.setProperty("M1_ALT", m1Bind.alt.toString())

    props.setProperty("M2_KEY", m2Bind.keyChar)
    props.setProperty("M2_SHIFT", m2Bind.shift.toString())
    props.setProperty("M2_CTRL", m2Bind.ctrl.toString())
    props.setProperty("M2_ALT", m2Bind.alt.toString())

    props.setProperty("M3_KEY", m3Bind.keyChar)
    props.setProperty("M3_SHIFT", m3Bind.shift.toString())
    props.setProperty("M3_CTRL", m3Bind.ctrl.toString())
    props.setProperty("M3_ALT", m3Bind.alt.toString())

    props.setProperty("M4_KEY", m4Bind.keyChar)
    props.setProperty("M4_SHIFT", m4Bind.shift.toString())
    props.setProperty("M4_CTRL", m4Bind.ctrl.toString())
    props.setProperty("M4_ALT", m4Bind.alt.toString())

    FileOutputStream(configFile).use { out ->
        props.store(out, "ShadowLink Paddle Configuration")
    }
}

/**
 * Controller Polling Loop
 */
fun runControllerSniffer() {
    val hidServices = HidManager.getHidServices()
    val robot = Robot()
    robot.isAutoWaitForIdle = false

    println("Background Service: Starting hardware scanner...")

    while (true) {
        val asusDevices = hidServices.attachedHidDevices.filter { it.vendorId == 0x0B05 }
        val raikiri = asusDevices.find { Integer.toHexString(it.usagePage).endsWith("c3") }

        if (raikiri != null && raikiri.open()) {
            println("Background Service Active: Connected to Raikiri II. Listening for Paddles...")

            var m1WasPressed = false
            var m2WasPressed = false
            var m3WasPressed = false
            var m4WasPressed = false

            var isConnected = true

            while (isConnected) {
                val data = ByteArray(64)
                // Read very fast (2ms timeout) to drain the USB buffer instantly
                val bytesRead = raikiri.read(data, 2)

                if (bytesRead > 0) {
                    val reportId = data[0].toInt() and 0xFF

                    if (reportId == 0xB3) {
                        val m1IsPressed = data[8].toInt() == 1
                        val m2IsPressed = data[6].toInt() == 1
                        val m3IsPressed = data[5].toInt() == 1
                        val m4IsPressed = data[7].toInt() == 1

                        // --- M1 STATE MACHINE ---
                        if (m1IsPressed && !m1WasPressed) {
                            pressKeyBind(robot, m1Bind)
                        } else if (!m1IsPressed && m1WasPressed) {
                            releaseKeyBind(robot, m1Bind)
                        }
                        m1WasPressed = m1IsPressed

                        // --- M2 STATE MACHINE ---
                        if (m2IsPressed && !m2WasPressed) {
                            pressKeyBind(robot, m2Bind)
                        } else if (!m2IsPressed && m2WasPressed) {
                            releaseKeyBind(robot, m2Bind)
                        }
                        m2WasPressed = m2IsPressed

                        // --- M3 STATE MACHINE ---
                        if (m3IsPressed && !m3WasPressed) {
                            pressKeyBind(robot, m3Bind)
                        } else if (!m3IsPressed && m3WasPressed) {
                            releaseKeyBind(robot, m3Bind)
                        }
                        m3WasPressed = m3IsPressed

                        // --- M4 STATE MACHINE ---
                        if (m4IsPressed && !m4WasPressed) {
                            pressKeyBind(robot, m4Bind)
                        } else if (!m4IsPressed && m4WasPressed) {
                            releaseKeyBind(robot, m4Bind)
                        }
                        m4WasPressed = m4IsPressed
                    }
                } else if (bytesRead < 0) {
                    println("Background Service: Controller disconnected. Waiting for reconnect...")

                    releaseKeyBind(robot, m1Bind)
                    releaseKeyBind(robot, m2Bind)
                    releaseKeyBind(robot, m3Bind)
                    releaseKeyBind(robot, m4Bind)

                    raikiri.close()
                    isConnected = false
                }
            }
        } else {
            Thread.sleep(2000)
        }
    }
}

/**
 * Keyboard Execution Functions
 */
fun getKeyCode(bind: PaddleBind): Int? {
    return KEY_MAP.entries.firstOrNull { it.key.equals(bind.keyChar, ignoreCase = true) }?.value
}

// Push-to-Talk "Down"
fun pressKeyBind(robot: Robot, bind: PaddleBind) {
    val keyCode = getKeyCode(bind) ?: return
    try {
        if (bind.shift) robot.keyPress(KeyEvent.VK_SHIFT)
        if (bind.ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
        if (bind.alt) robot.keyPress(KeyEvent.VK_ALT)

        robot.keyPress(keyCode)
    } catch (e: Exception) {}
}

// Push-to-Talk "Up"
fun releaseKeyBind(robot: Robot, bind: PaddleBind) {
    val keyCode = getKeyCode(bind) ?: return
    try {
        robot.keyRelease(keyCode)

        if (bind.alt) robot.keyRelease(KeyEvent.VK_ALT)
        if (bind.ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
        if (bind.shift) robot.keyRelease(KeyEvent.VK_SHIFT)
    } catch (e: Exception) {}
}