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

// Global memory for our bindings so the background thread can see them instantly when changed
val m1Bind = PaddleBind("J", shift = false, ctrl = false, alt = false)
val m2Bind = PaddleBind("L", shift = false, ctrl = false, alt = false)
val m3Bind = PaddleBind("G", shift = false, ctrl = false, alt = false)
val m4Bind = PaddleBind("M", shift = false, ctrl = false, alt = false)

val configFile = File("config.properties")

fun main() {
    // 1. Load existing configurations from file into our global memory
    loadConfig()

    // 2. Launch the graphical user interface (GUI) on the Swing Event Dispatch Thread
    SwingUtilities.invokeLater {
        createAndShowGUI()
    }

    // 3. Launch the controller sniffer on a background thread so it doesn't freeze the GUI
    Thread {
        runControllerSniffer()
    }.start()
}

/**
 * Creates the lightweight Swing UI window.
 */
fun createAndShowGUI() {
    // Force the UI to look like a native Windows app instead of an old Java app
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

        // Globally increase the font size for modern high-DPI displays
        val baseFont = Font("Segoe UI", Font.PLAIN, 14)
        UIManager.put("Label.font", baseFont)
        UIManager.put("CheckBox.font", baseFont)
        UIManager.put("TextField.font", baseFont)
        UIManager.put("Button.font", baseFont)
    } catch (e: Exception) {
        // Ignore if system look and feel fails
    }

    val frame = JFrame("ShadowLink - ROG Raikiri II")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(450, 350) // Slightly larger to accommodate the bigger fonts
    frame.setLocationRelativeTo(null) // Center on screen
    frame.layout = GridLayout(6, 1, 5, 5)

    // Header
    val headerLabel = JLabel("Configure Back Paddles", SwingConstants.CENTER)
    headerLabel.font = Font("Segoe UI", Font.BOLD, 18) // Increased header size
    frame.add(headerLabel)

    // Create rows for each paddle mapped to their explicit physical positions
    val m1Controls = createPaddleRow("M1 (Bottom Left)", m1Bind)
    val m2Controls = createPaddleRow("M2 (Top Left)", m2Bind)
    val m3Controls = createPaddleRow("M3 (Top Right)", m3Bind)
    val m4Controls = createPaddleRow("M4 (Bottom Right)", m4Bind)

    frame.add(m1Controls.panel)
    frame.add(m2Controls.panel)
    frame.add(m3Controls.panel)
    frame.add(m4Controls.panel)

    // Save Button Row
    val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
    val saveButton = JButton("Save & Apply")
    saveButton.addActionListener {
        // Read values from the UI text boxes and checkboxes, update memory
        updateBindFromUI(m1Bind, m1Controls)
        updateBindFromUI(m2Bind, m2Controls)
        updateBindFromUI(m3Bind, m3Controls)
        updateBindFromUI(m4Bind, m4Controls)

        // Save to config.properties
        saveConfig()
        JOptionPane.showMessageDialog(frame, "Bindings Saved and Applied instantly!", "Success", JOptionPane.INFORMATION_MESSAGE)
    }
    buttonPanel.add(saveButton)
    frame.add(buttonPanel)

    frame.isVisible = true
}

// A helper class to hold references to our UI inputs so we can read them when clicking Save
class PaddleUIControls(
    val panel: JPanel,
    val shiftBox: JCheckBox,
    val ctrlBox: JCheckBox,
    val altBox: JCheckBox,
    val keyInput: JTextField
)

fun createPaddleRow(name: String, bind: PaddleBind): PaddleUIControls {
    val panel = JPanel(FlowLayout(FlowLayout.CENTER))
    panel.add(JLabel("$name: "))

    val shiftBox = JCheckBox("Shift", bind.shift)
    val ctrlBox = JCheckBox("Ctrl", bind.ctrl)
    val altBox = JCheckBox("Alt", bind.alt)
    val keyInput = JTextField(bind.keyChar, 3)
    keyInput.horizontalAlignment = JTextField.CENTER

    panel.add(shiftBox)
    panel.add(ctrlBox)
    panel.add(altBox)
    panel.add(JLabel(" + "))
    panel.add(keyInput)

    return PaddleUIControls(panel, shiftBox, ctrlBox, altBox, keyInput)
}

fun updateBindFromUI(bind: PaddleBind, controls: PaddleUIControls) {
    bind.shift = controls.shiftBox.isSelected
    bind.ctrl = controls.ctrlBox.isSelected
    bind.alt = controls.altBox.isSelected

    // Grab the first letter of whatever they typed in the box
    val inputText = controls.keyInput.text.trim().uppercase()
    if (inputText.isNotEmpty()) {
        bind.keyChar = inputText[0].toString()
    } else {
        bind.keyChar = ""
    }
    controls.keyInput.text = bind.keyChar // Force UI to show the single char
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
 * Controller Polling Loop (Runs silently in the background)
 */
fun runControllerSniffer() {
    val hidServices = HidManager.getHidServices()
    val robot = Robot()

    println("Background Service: Starting hardware scanner...")

    // OUTER LOOP: Continuously scans for the controller if it gets disconnected
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

            // INNER LOOP: Fast polling while the controller is actively connected
            while (isConnected) {
                val data = ByteArray(64)
                val bytesRead = raikiri.read(data, 5)

                if (bytesRead > 0) {
                    val reportId = data[0].toInt() and 0xFF

                    if (reportId == 0xB3) {
                        val m1IsPressed = data[8].toInt() == 1 // Bottom Left
                        val m2IsPressed = data[6].toInt() == 1 // Top Left
                        val m3IsPressed = data[5].toInt() == 1 // Top Right
                        val m4IsPressed = data[7].toInt() == 1 // Bottom Right

                        // M1
                        if (m1IsPressed && !m1WasPressed) executeKeyBind(robot, m1Bind)
                        m1WasPressed = m1IsPressed

                        // M2
                        if (m2IsPressed && !m2WasPressed) executeKeyBind(robot, m2Bind)
                        m2WasPressed = m2IsPressed

                        // M3
                        if (m3IsPressed && !m3WasPressed) executeKeyBind(robot, m3Bind)
                        m3WasPressed = m3IsPressed

                        // M4
                        if (m4IsPressed && !m4WasPressed) executeKeyBind(robot, m4Bind)
                        m4WasPressed = m4IsPressed
                    }
                } else if (bytesRead < 0) {
                    // A return value of -1 means the USB device was physically disconnected or errored out.
                    // (A return of 0 just means it timed out waiting for new data, which is normal).
                    println("Background Service: Controller disconnected. Waiting for reconnect...")
                    raikiri.close()
                    isConnected = false // Break the inner loop, resume the outer scanning loop
                }
            }
        } else {
            // The controller wasn't found. Sleep for 2 seconds so we don't melt the CPU, then check again.
            Thread.sleep(2000)
        }
    }
}

/**
 * Translates our PaddleBind into actual Java Robot keyboard strikes (including modifiers)
 */
fun executeKeyBind(robot: Robot, bind: PaddleBind) {
    if (bind.keyChar.isEmpty()) return

    val keyCode = KeyEvent.getExtendedKeyCodeForChar(bind.keyChar[0].code)
    if (keyCode == KeyEvent.VK_UNDEFINED) return

    try {
        // 1. Press Modifiers Down
        if (bind.shift) robot.keyPress(KeyEvent.VK_SHIFT)
        if (bind.ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
        if (bind.alt) robot.keyPress(KeyEvent.VK_ALT)

        // 2. Strike the main key
        robot.keyPress(keyCode)
        robot.keyRelease(keyCode)

        // 3. Release Modifiers (in reverse order is best practice)
        if (bind.alt) robot.keyRelease(KeyEvent.VK_ALT)
        if (bind.ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
        if (bind.shift) robot.keyRelease(KeyEvent.VK_SHIFT)

    } catch (e: Exception) {
        // Silent catch
    }
}