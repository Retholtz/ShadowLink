package com.retholtz.shadowlink

import org.hid4java.HidManager
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import javax.swing.*

// Data class to hold the configuration for a single paddle
data class PaddleBind(
    var enabled: Boolean = true,
    var isMacro: Boolean = false,
    var macroText: String = "",
    var keyChar: String = "A",
    var shift: Boolean = false,
    var ctrl: Boolean = false,
    var alt: Boolean = false,
    var win: Boolean = false
)

// Global memory for our bindings
val m1Bind = PaddleBind(keyChar = "J")
val m2Bind = PaddleBind(keyChar = "L")
val m3Bind = PaddleBind(keyChar = "G")
val m4Bind = PaddleBind(keyChar = "M")

var startMinimized = false
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
    "`" to KeyEvent.VK_BACK_QUOTE,

    // Modifiers added for Macro parsing
    "Shift" to KeyEvent.VK_SHIFT, "Ctrl" to KeyEvent.VK_CONTROL, "Alt" to KeyEvent.VK_ALT, "Win" to KeyEvent.VK_WINDOWS
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
        UIManager.put("TextField.font", baseFont)
    } catch (e: Exception) {
        // Ignore
    }

    val frame = JFrame("ShadowLink - ROG Raikiri II")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(850, 420) // Widened to accommodate the 'Enabled' checkbox
    frame.setLocationRelativeTo(null)
    frame.layout = GridLayout(7, 1, 5, 5)

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

    // Save and Options Panel
    val optionsPanel = JPanel(FlowLayout(FlowLayout.CENTER))
    val minimizedBox = JCheckBox("Start Minimized to System Tray", startMinimized)
    minimizedBox.addActionListener { startMinimized = minimizedBox.isSelected }
    optionsPanel.add(minimizedBox)
    frame.add(optionsPanel)

    // Bottom Button Panel (Macro Instructions on Left, Save on Right)
    val buttonPanel = JPanel(BorderLayout())
    buttonPanel.border = BorderFactory.createEmptyBorder(0, 20, 10, 20)

    val helpButton = JButton("Macro Instructions")
    helpButton.addActionListener { showMacroInstructions(frame) }
    buttonPanel.add(helpButton, BorderLayout.WEST)

    val saveButton = JButton("Save & Apply")
    saveButton.addActionListener {
        updateBindFromUI(m1Bind, m1Controls)
        updateBindFromUI(m2Bind, m2Controls)
        updateBindFromUI(m3Bind, m3Controls)
        updateBindFromUI(m4Bind, m4Controls)
        saveConfig()
        JOptionPane.showMessageDialog(frame, "Bindings Saved and Applied instantly!", "Success", JOptionPane.INFORMATION_MESSAGE)
    }
    buttonPanel.add(saveButton, BorderLayout.EAST)

    frame.add(buttonPanel)

    setupSystemTray(frame)
}

/**
 * Displays a popup dialog with instructions on how to write macros.
 */
fun showMacroInstructions(parent: JFrame) {
    val instructions = """
        How to Build Macros:
        Macros are a sequence of key presses and delays separated by commas.
        
        1. Simple Taps:
           Type the name of the key to press and release it immediately.
           Example: A, B, Enter
           
        2. Holding & Releasing Keys:
           Add " down" to hold a key, and " up" to release it.
           Modifiers (Shift, Ctrl, Alt, Win) usually need this!
           Example: Shift down, A, Shift up
           
        3. Pauses (Delays):
           Type a number to pause the macro for that many milliseconds.
           (1000 = 1 second, 50 = very short pause).
           Example: A, 500, B
           
        4. Special Key Names:
           Arrows: Up, Down, Left, Right
           Modifiers: Shift, Ctrl, Alt, Win
           System: Space, Enter, Tab, ESC, Backspace, Delete, Insert
           F-Keys: F1, F2 ... F12
           
        Example Macro (Save a file: Ctrl+S):
        Ctrl down, S, 50, Ctrl up
        
        Example Macro (Run forward in a game, then jump):
        W down, 2000, Space, W up
    """.trimIndent()

    val textArea = JTextArea(instructions)
    textArea.isEditable = false
    textArea.font = Font("Monospaced", Font.PLAIN, 13)
    textArea.background = UIManager.getColor("Panel.background")
    textArea.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

    JOptionPane.showMessageDialog(
        parent,
        textArea,
        "Macro Instructions",
        JOptionPane.INFORMATION_MESSAGE
    )
}

fun setupSystemTray(frame: JFrame) {
    if (!SystemTray.isSupported()) {
        frame.isVisible = true
        return
    }

    val tray = SystemTray.getSystemTray()

    // 1. Attempt to load your custom icon.png from the resources folder
    val iconUrl = object {}.javaClass.getResource("/icon.png")

    val img: Image = if (iconUrl != null) {
        Toolkit.getDefaultToolkit().getImage(iconUrl)
    } else {
        // Fallback: Draw the blue circle if the file is missing
        println("Warning: icon.png not found in resources. Using fallback icon.")
        val fallbackImg = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g = fallbackImg.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0, 120, 215)
        g.fillOval(2, 2, 28, 28)
        g.color = Color.WHITE
        g.drawOval(2, 2, 28, 28)
        g.font = Font("Segoe UI", Font.BOLD, 14)
        g.drawString("SL", 8, 22)
        g.dispose()
        fallbackImg
    }

    val trayIcon = TrayIcon(img, "ShadowLink")
    trayIcon.isImageAutoSize = true // This automatically scales your PNG to fit the Windows Tray perfectly

    val popup = PopupMenu()
    val openItem = MenuItem("Open ShadowLink")
    openItem.addActionListener {
        frame.isVisible = true
        frame.state = Frame.NORMAL
    }
    val exitItem = MenuItem("Exit")
    exitItem.addActionListener { System.exit(0) }

    popup.add(openItem)
    popup.addSeparator()
    popup.add(exitItem)
    trayIcon.popupMenu = popup

    // Left-click to open
    trayIcon.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.button == MouseEvent.BUTTON1) {
                frame.isVisible = true
                frame.state = Frame.NORMAL
            }
        }
    })

    // Add the tray icon ONCE at startup
    try {
        tray.add(trayIcon)
    } catch (e: AWTException) {
        println("Warning: Could not add icon to system tray.")
    }

    // Intercept the minimize action to hide the window from the taskbar
    frame.addWindowStateListener { e ->
        if (e.newState == Frame.ICONIFIED) {
            frame.isVisible = false
        }
    }

    // Handle startup visibility based on the saved setting
    if (startMinimized) {
        frame.isVisible = false
    } else {
        frame.isVisible = true
    }
}

class PaddleUIControls(
    val panel: JPanel,
    val enabledBox: JCheckBox,
    val isMacroBox: JCheckBox,
    val macroField: JTextField,
    val shiftBox: JCheckBox,
    val ctrlBox: JCheckBox,
    val altBox: JCheckBox,
    val winBox: JCheckBox,
    val keyDropdown: JComboBox<String>
)

fun createPaddleRow(name: String, bind: PaddleBind): PaddleUIControls {
    val panel = JPanel(FlowLayout(FlowLayout.CENTER))
    panel.add(JLabel("$name: "))

    val enabledBox = JCheckBox("Enabled", bind.enabled)
    val isMacroBox = JCheckBox("Macro", bind.isMacro)
    val macroField = JTextField(bind.macroText, 20)
    macroField.toolTipText = "Syntax: Key [down|up], delayMs (e.g. A down, 50, B, 100, A up)"

    val shiftBox = JCheckBox("Shift", bind.shift)
    val ctrlBox = JCheckBox("Ctrl", bind.ctrl)
    val altBox = JCheckBox("Alt", bind.alt)
    val winBox = JCheckBox("Win", bind.win)

    val keyDropdown = JComboBox(SUPPORTED_KEYS)
    val match = SUPPORTED_KEYS.firstOrNull { it.equals(bind.keyChar, ignoreCase = true) }
    keyDropdown.selectedItem = match ?: "A"

    fun updateVisibility() {
        val isEnabled = enabledBox.isSelected
        val macroMode = isMacroBox.isSelected

        // Toggle interactivity based on Enabled state
        isMacroBox.isEnabled = isEnabled
        macroField.isEnabled = isEnabled && macroMode
        shiftBox.isEnabled = isEnabled && !macroMode
        ctrlBox.isEnabled = isEnabled && !macroMode
        altBox.isEnabled = isEnabled && !macroMode
        winBox.isEnabled = isEnabled && !macroMode
        keyDropdown.isEnabled = isEnabled && !macroMode

        // Toggle visibility based on Macro state
        macroField.isVisible = macroMode
        shiftBox.isVisible = !macroMode
        ctrlBox.isVisible = !macroMode
        altBox.isVisible = !macroMode
        winBox.isVisible = !macroMode
        keyDropdown.isVisible = !macroMode

        panel.revalidate()
        panel.repaint()
    }

    enabledBox.addActionListener { updateVisibility() }
    isMacroBox.addActionListener { updateVisibility() }

    panel.add(enabledBox)
    panel.add(isMacroBox)
    panel.add(shiftBox)
    panel.add(ctrlBox)
    panel.add(altBox)
    panel.add(winBox)
    panel.add(JLabel(" + "))
    panel.add(keyDropdown)
    panel.add(macroField)

    updateVisibility()

    return PaddleUIControls(panel, enabledBox, isMacroBox, macroField, shiftBox, ctrlBox, altBox, winBox, keyDropdown)
}

fun updateBindFromUI(bind: PaddleBind, controls: PaddleUIControls) {
    bind.enabled = controls.enabledBox.isSelected
    bind.isMacro = controls.isMacroBox.isSelected
    bind.macroText = controls.macroField.text

    bind.shift = controls.shiftBox.isSelected
    bind.ctrl = controls.ctrlBox.isSelected
    bind.alt = controls.altBox.isSelected
    bind.win = controls.winBox.isSelected

    bind.keyChar = controls.keyDropdown.selectedItem?.toString() ?: "A"
}

/**
 * Configuration File Management
 */
fun loadConfig() {
    val props = Properties()
    if (configFile.exists()) {
        FileInputStream(configFile).use { input -> props.load(input) }

        startMinimized = props.getProperty("START_MINIMIZED", "false").toBoolean()

        fun loadBind(prefix: String, bind: PaddleBind, defaultKey: String) {
            bind.enabled = props.getProperty("${prefix}_ENABLED", "true").toBoolean()
            bind.isMacro = props.getProperty("${prefix}_ISMACRO", "false").toBoolean()
            bind.macroText = props.getProperty("${prefix}_MACROTEXT", "")
            bind.keyChar = props.getProperty("${prefix}_KEY", defaultKey)
            bind.shift = props.getProperty("${prefix}_SHIFT", "false").toBoolean()
            bind.ctrl = props.getProperty("${prefix}_CTRL", "false").toBoolean()
            bind.alt = props.getProperty("${prefix}_ALT", "false").toBoolean()
            bind.win = props.getProperty("${prefix}_WIN", "false").toBoolean()
        }

        loadBind("M1", m1Bind, "J")
        loadBind("M2", m2Bind, "L")
        loadBind("M3", m3Bind, "G")
        loadBind("M4", m4Bind, "M")
    }
}

fun saveConfig() {
    val props = Properties()

    props.setProperty("START_MINIMIZED", startMinimized.toString())

    fun saveBind(prefix: String, bind: PaddleBind) {
        props.setProperty("${prefix}_ENABLED", bind.enabled.toString())
        props.setProperty("${prefix}_ISMACRO", bind.isMacro.toString())
        props.setProperty("${prefix}_MACROTEXT", bind.macroText)
        props.setProperty("${prefix}_KEY", bind.keyChar)
        props.setProperty("${prefix}_SHIFT", bind.shift.toString())
        props.setProperty("${prefix}_CTRL", bind.ctrl.toString())
        props.setProperty("${prefix}_ALT", bind.alt.toString())
        props.setProperty("${prefix}_WIN", bind.win.toString())
    }

    saveBind("M1", m1Bind)
    saveBind("M2", m2Bind)
    saveBind("M3", m3Bind)
    saveBind("M4", m4Bind)

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
                val bytesRead = raikiri.read(data, 2)

                if (bytesRead > 0) {
                    val reportId = data[0].toInt() and 0xFF

                    if (reportId == 0xB3) {
                        val m1IsPressed = data[8].toInt() == 1
                        val m2IsPressed = data[6].toInt() == 1
                        val m3IsPressed = data[5].toInt() == 1
                        val m4IsPressed = data[7].toInt() == 1

                        // --- M1 STATE MACHINE ---
                        if (m1Bind.enabled) {
                            if (m1IsPressed && !m1WasPressed) {
                                if (m1Bind.isMacro) executeMacro(robot, m1Bind.macroText) else pressKeyBind(robot, m1Bind)
                            } else if (!m1IsPressed && m1WasPressed) {
                                if (!m1Bind.isMacro) releaseKeyBind(robot, m1Bind)
                            }
                        }
                        m1WasPressed = m1IsPressed

                        // --- M2 STATE MACHINE ---
                        if (m2Bind.enabled) {
                            if (m2IsPressed && !m2WasPressed) {
                                if (m2Bind.isMacro) executeMacro(robot, m2Bind.macroText) else pressKeyBind(robot, m2Bind)
                            } else if (!m2IsPressed && m2WasPressed) {
                                if (!m2Bind.isMacro) releaseKeyBind(robot, m2Bind)
                            }
                        }
                        m2WasPressed = m2IsPressed

                        // --- M3 STATE MACHINE ---
                        if (m3Bind.enabled) {
                            if (m3IsPressed && !m3WasPressed) {
                                if (m3Bind.isMacro) executeMacro(robot, m3Bind.macroText) else pressKeyBind(robot, m3Bind)
                            } else if (!m3IsPressed && m3WasPressed) {
                                if (!m3Bind.isMacro) releaseKeyBind(robot, m3Bind)
                            }
                        }
                        m3WasPressed = m3IsPressed

                        // --- M4 STATE MACHINE ---
                        if (m4Bind.enabled) {
                            if (m4IsPressed && !m4WasPressed) {
                                if (m4Bind.isMacro) executeMacro(robot, m4Bind.macroText) else pressKeyBind(robot, m4Bind)
                            } else if (!m4IsPressed && m4WasPressed) {
                                if (!m4Bind.isMacro) releaseKeyBind(robot, m4Bind)
                            }
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
fun getKeyCode(keyChar: String): Int? {
    return KEY_MAP.entries.firstOrNull { it.key.equals(keyChar, ignoreCase = true) }?.value
}

fun pressKeyBind(robot: Robot, bind: PaddleBind) {
    val keyCode = getKeyCode(bind.keyChar) ?: return
    try {
        if (bind.win) robot.keyPress(KeyEvent.VK_WINDOWS)
        if (bind.shift) robot.keyPress(KeyEvent.VK_SHIFT)
        if (bind.ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
        if (bind.alt) robot.keyPress(KeyEvent.VK_ALT)

        robot.keyPress(keyCode)
    } catch (e: Exception) {}
}

fun releaseKeyBind(robot: Robot, bind: PaddleBind) {
    val keyCode = getKeyCode(bind.keyChar) ?: return
    try {
        robot.keyRelease(keyCode)

        if (bind.alt) robot.keyRelease(KeyEvent.VK_ALT)
        if (bind.ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
        if (bind.shift) robot.keyRelease(KeyEvent.VK_SHIFT)
        if (bind.win) robot.keyRelease(KeyEvent.VK_WINDOWS)
    } catch (e: Exception) {}
}

/**
 * Parses and executes a comma-separated macro string on a separate Thread.
 */
fun executeMacro(robot: Robot, macroText: String) {
    Thread {
        val tokens = macroText.split(",").map { it.trim() }
        for (token in tokens) {
            if (token.isEmpty()) continue

            val delay = token.toLongOrNull()
            if (delay != null) {
                Thread.sleep(delay)
            } else {
                val parts = token.split(" ").map { it.trim() }
                val keyStr = parts[0]
                val action = if (parts.size > 1) parts[1].lowercase() else "tap"

                val keyCode = getKeyCode(keyStr)
                if (keyCode != null) {
                    try {
                        when (action) {
                            "down" -> robot.keyPress(keyCode)
                            "up" -> robot.keyRelease(keyCode)
                            else -> {
                                robot.keyPress(keyCode)
                                Thread.sleep(20)
                                robot.keyRelease(keyCode)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }.start()
}