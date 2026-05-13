package com.retholtz.shadowlink

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import org.hid4java.HidDevice
import org.hid4java.HidManager
import org.hid4java.HidServicesListener
import org.hid4java.HidServicesSpecification
import org.hid4java.event.HidServicesEvent
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.util.Properties
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.random.Random

// --- GLOBAL STATE ---
const val APP_VERSION = "1.25"
const val GITHUB_REPO = "retholtz/ShadowLink"

var profiles = mutableListOf<Profile>()
var activeProfile: Profile = Profile()
var autoSwitchEnabled = true
var startMinimized = false
var loadOnStartup = false
var isDarkMode = true
var osdPosition = "Bottom Right"
var controllerScanInterval = 5000 // Global Auto-Detect Rate in ms
var comboBufferMs = 30 // Microlag buffer to wait for combo presses

@Volatile var activeLayer = 1 // Tracks current layer (1 through 5)

val appDir = try {
    File(PaddleBind::class.java.protectionDomain.codeSource.location.toURI()).parentFile
} catch (e: Exception) {
    File(System.getProperty("user.dir"))
}

val rootDir = File(appDir, "profiles").apply { if (!exists()) mkdirs() }
val globalConfigFile = File(appDir, "config.properties")

// --- DATA STRUCTURES ---

data class PaddleBind(
    var enabled: Boolean = true,
    var isMacro: Boolean = false,
    var repeatMacro: Boolean = false,
    var stepThrough: Boolean = false,
    var macroText: String = "",
    var keyChar: String = "A",
    var shift: Boolean = false,
    var ctrl: Boolean = false,
    var alt: Boolean = false,
    var win: Boolean = false
)

data class LayerConfig(
    var name: String = "Layer",
    var enabled: Boolean = false, // Layer 1 will always be forced true in logic
    val m1: PaddleBind = PaddleBind(keyChar = "J"),
    val m2: PaddleBind = PaddleBind(keyChar = "L"),
    val m3: PaddleBind = PaddleBind(keyChar = "G"),
    val m4: PaddleBind = PaddleBind(keyChar = "M"),
    val cmd: PaddleBind = PaddleBind(keyChar = "C"),
    val lib: PaddleBind = PaddleBind(keyChar = "V"),

    // Combo Binds (Disabled by default)
    val m1_m2: PaddleBind = PaddleBind(enabled = false),
    val m1_m3: PaddleBind = PaddleBind(enabled = false),
    val m1_m4: PaddleBind = PaddleBind(enabled = false),
    val m2_m3: PaddleBind = PaddleBind(enabled = false),
    val m2_m4: PaddleBind = PaddleBind(enabled = false),
    val m3_m4: PaddleBind = PaddleBind(enabled = false)
)

data class Profile(
    var name: String = "Default",
    var targetProcess: String = "",
    var toggleButton1: String = "None",
    var toggleButton2: String = "None",
    val layers: List<LayerConfig> = listOf(
        LayerConfig("Layer 1", true),
        LayerConfig("Layer 2", false, m1=PaddleBind(keyChar="1"), m2=PaddleBind(keyChar="2"), m3=PaddleBind(keyChar="3"), m4=PaddleBind(keyChar="4"), cmd=PaddleBind(keyChar="5"), lib=PaddleBind(keyChar="6")),
        LayerConfig("Layer 3", false, m1=PaddleBind(keyChar="7"), m2=PaddleBind(keyChar="8"), m3=PaddleBind(keyChar="9"), m4=PaddleBind(keyChar="0"), cmd=PaddleBind(keyChar="-"), lib=PaddleBind(keyChar="=")),
        LayerConfig("Layer 4", false),
        LayerConfig("Layer 5", false)
    )
)

// --- KEY MAPPING ---

val SUPPORTED_KEYS = arrayOf(
    "LClick", "RClick", "MClick",
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
    "`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "Backspace",
    "ESC", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
    "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24",
    "Insert", "Home", "Page Up", "Delete", "End", "Page Down",
    "Space", "Enter", "Tab", "Up", "Down", "Left", "Right",
    ",", ".", "/", "\\", ";", "'", "[", "]",
    "NumPad 0", "NumPad 1", "NumPad 2", "NumPad 3", "NumPad 4", "NumPad 5", "NumPad 6", "NumPad 7", "NumPad 8", "NumPad 9",
    "NumPad /", "NumPad *", "NumPad -", "NumPad +", "NumPad .", "NumPad Enter"
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
    "F13" to KeyEvent.VK_F13, "F14" to KeyEvent.VK_F14, "F15" to KeyEvent.VK_F15, "F16" to KeyEvent.VK_F16,
    "F17" to KeyEvent.VK_F17, "F18" to KeyEvent.VK_F18, "F19" to KeyEvent.VK_F19, "F20" to KeyEvent.VK_F20,
    "F21" to KeyEvent.VK_F21, "F22" to KeyEvent.VK_F22, "F23" to KeyEvent.VK_F23, "F24" to KeyEvent.VK_F24,
    "Insert" to KeyEvent.VK_INSERT, "Delete" to KeyEvent.VK_DELETE, "Home" to KeyEvent.VK_HOME, "End" to KeyEvent.VK_END,
    "Page Up" to KeyEvent.VK_PAGE_UP, "Page Down" to KeyEvent.VK_PAGE_DOWN,
    "Space" to KeyEvent.VK_SPACE, "Enter" to KeyEvent.VK_ENTER, "Tab" to KeyEvent.VK_TAB, "ESC" to KeyEvent.VK_ESCAPE, "Backspace" to KeyEvent.VK_BACK_SPACE,
    "Up" to KeyEvent.VK_UP, "Down" to KeyEvent.VK_DOWN, "Left" to KeyEvent.VK_LEFT, "Right" to KeyEvent.VK_RIGHT,
    "-" to KeyEvent.VK_MINUS, "=" to KeyEvent.VK_EQUALS, "," to KeyEvent.VK_COMMA, "." to KeyEvent.VK_PERIOD,
    "/" to KeyEvent.VK_SLASH, "\\" to KeyEvent.VK_BACK_SLASH, ";" to KeyEvent.VK_SEMICOLON,
    "'" to KeyEvent.VK_QUOTE, "[" to KeyEvent.VK_OPEN_BRACKET, "]" to KeyEvent.VK_CLOSE_BRACKET,
    "`" to KeyEvent.VK_BACK_QUOTE, "Shift" to KeyEvent.VK_SHIFT, "Ctrl" to KeyEvent.VK_CONTROL, "Alt" to KeyEvent.VK_ALT, "Win" to KeyEvent.VK_WINDOWS,
    "NumPad 0" to KeyEvent.VK_NUMPAD0, "NumPad 1" to KeyEvent.VK_NUMPAD1, "NumPad 2" to KeyEvent.VK_NUMPAD2,
    "NumPad 3" to KeyEvent.VK_NUMPAD3, "NumPad 4" to KeyEvent.VK_NUMPAD4, "NumPad 5" to KeyEvent.VK_NUMPAD5,
    "NumPad 6" to KeyEvent.VK_NUMPAD6, "NumPad 7" to KeyEvent.VK_NUMPAD7, "NumPad 8" to KeyEvent.VK_NUMPAD8,
    "NumPad 9" to KeyEvent.VK_NUMPAD9, "NumPad /" to KeyEvent.VK_DIVIDE, "NumPad *" to KeyEvent.VK_MULTIPLY,
    "NumPad -" to KeyEvent.VK_SUBTRACT, "NumPad +" to KeyEvent.VK_ADD, "NumPad ." to KeyEvent.VK_DECIMAL,
    "NumPad Enter" to KeyEvent.VK_ENTER
)

// --- OSD SYSTEM ---

var osdWindow: JWindow? = null
var osdTimer: javax.swing.Timer? = null

fun showOSD(message: String) {
    SwingUtilities.invokeLater {
        osdWindow?.dispose()
        osdTimer?.stop()

        val w = JWindow()
        w.isAlwaysOnTop = true
        w.focusableWindowState = false
        w.background = Color(0, 0, 0, 0)

        val panel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                g2.color = Color(0, 0, 0, 180)
                g2.fillRoundRect(0, 0, width, height, 25, 25)

                g2.color = Color.WHITE
                g2.font = Font("Segoe UI", Font.BOLD, 22)
                val fm = g2.fontMetrics
                val x = (width - fm.stringWidth(message)) / 2
                val y = (height - fm.height) / 2 + fm.ascent
                g2.drawString(message, x, y)
            }
        }
        panel.isOpaque = false

        val dummyImg = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val fm = dummyImg.createGraphics().apply { font = Font("Segoe UI", Font.BOLD, 22) }.fontMetrics
        val wWidth = fm.stringWidth(message) + 80
        val wHeight = fm.height + 40

        w.contentPane.add(panel)
        w.setSize(wWidth, wHeight)

        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val gd = ge.defaultScreenDevice
        val bounds = gd.defaultConfiguration.bounds

        val margin = 40
        val x = when (osdPosition) {
            "Bottom Right", "Top Right" -> bounds.width - wWidth - margin
            else -> bounds.x + margin
        }
        val y = when (osdPosition) {
            "Bottom Right", "Bottom Left" -> bounds.height - wHeight - margin
            else -> bounds.y + margin
        }
        w.setLocation(x, y)

        w.isVisible = true
        osdWindow = w

        osdTimer = javax.swing.Timer(2000) {
            w.dispose()
        }.apply {
            isRepeats = false
            start()
        }
    }
}

// --- MAIN ENTRY ---

fun main() {
    System.setProperty("java.awt.headless", "false")
    loadAllProfiles()

    SwingUtilities.invokeLater {
        createAndShowGUI()

        Thread {
            Thread.sleep(1500)
            runControllerSniffer()
        }.start()

        Thread {
            Thread.sleep(1500)
            runAutoSwitchWatchdog()
        }.start()
    }
}

// --- CORE UI ---

lateinit var profileCombo: JComboBox<String>
lateinit var processField: JTextField
lateinit var tabbedPane: JTabbedPane
lateinit var t1Combo: JComboBox<String>
lateinit var t2Combo: JComboBox<String>

class LayerUI(
    val nameField: JTextField, val enabledBox: JCheckBox,
    val m1: PaddleUIControls, val m2: PaddleUIControls,
    val m3: PaddleUIControls, val m4: PaddleUIControls,
    val cmd: PaddleUIControls, val lib: PaddleUIControls,
    val m1m2: PaddleUIControls, val m1m3: PaddleUIControls,
    val m1m4: PaddleUIControls, val m2m3: PaddleUIControls,
    val m2m4: PaddleUIControls, val m3m4: PaddleUIControls
)
val layerUIs = mutableListOf<LayerUI>()

fun refreshLayerLocks() {
    val t1 = t1Combo.selectedItem as String
    val t2 = t2Combo.selectedItem as String

    val isCombo = t1 != "None" && t2 != "None" && t1 != t2
    val reservedBtn = if (!isCombo) {
        if (t1 != "None") t1 else if (t2 != "None") t2 else "None"
    } else {
        "None"
    }

    val reservedCombo = if (isCombo) {
        val sorted = listOf(t1, t2).sorted()
        "${sorted[0]}+${sorted[1]}"
    } else {
        "None"
    }

    for (ui in layerUIs) {
        ui.m1.isReserved = (reservedBtn == "M1"); ui.m1.refreshVis()
        ui.m2.isReserved = (reservedBtn == "M2"); ui.m2.refreshVis()
        ui.m3.isReserved = (reservedBtn == "M3"); ui.m3.refreshVis()
        ui.m4.isReserved = (reservedBtn == "M4"); ui.m4.refreshVis()
        ui.cmd.isReserved = (reservedBtn == "Command"); ui.cmd.refreshVis()
        ui.lib.isReserved = (reservedBtn == "Library"); ui.lib.refreshVis()

        ui.m1m2.isReserved = (reservedCombo == "M1+M2"); ui.m1m2.refreshVis()
        ui.m1m3.isReserved = (reservedCombo == "M1+M3"); ui.m1m3.refreshVis()
        ui.m1m4.isReserved = (reservedCombo == "M1+M4"); ui.m1m4.refreshVis()
        ui.m2m3.isReserved = (reservedCombo == "M2+M3"); ui.m2m3.refreshVis()
        ui.m2m4.isReserved = (reservedCombo == "M2+M4"); ui.m2m4.refreshVis()
        ui.m3m4.isReserved = (reservedCombo == "M3+M4"); ui.m3m4.refreshVis()
    }
}

fun reloadProfileDropdown() {
    profileCombo.removeAllItems()
    profiles.forEach { profileCombo.addItem(it.name) }
    profileCombo.selectedItem = activeProfile.name
}

fun createAndShowGUI() {
    try {
        if (isDarkMode) UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf")
        else UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf")

        val baseFont = Font("Segoe UI", Font.PLAIN, 15)
        UIManager.put("defaultFont", baseFont)
        UIManager.put("TitledBorder.font", baseFont.deriveFont(Font.BOLD))
    } catch (e: Exception) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (ex: Exception) {}
    }

    val frame = JFrame("ShadowLink - ROG Raikiri II - v$APP_VERSION")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(1350, 850) // Widened for Dual Column
    frame.setLocationRelativeTo(null)
    frame.layout = BorderLayout(10, 10)

    val topPanel = JPanel()
    topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)
    topPanel.border = BorderFactory.createTitledBorder("Profile Management")

    val profileRowWrapper = JPanel(BorderLayout())

    val profileRowLeft = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
    profileCombo = JComboBox(profiles.map { it.name }.toTypedArray())
    profileCombo.preferredSize = Dimension(200, 30)
    profileCombo.selectedItem = activeProfile.name
    profileCombo.addActionListener {
        val selected = profileCombo.selectedItem as? String ?: return@addActionListener
        if (selected != activeProfile.name) {
            switchActiveProfile(selected)
            refreshUI()
        }
    }

    val newBtn = JButton("New Profile")
    newBtn.addActionListener {
        val name = JOptionPane.showInputDialog(frame, "Enter Profile Name:")
        if (!name.isNullOrBlank()) {
            val p = Profile(name = name)
            profiles.add(p)
            saveProfile(p)
            profileCombo.addItem(name)
            profileCombo.selectedItem = name
        }
    }

    val cloneBtn = JButton("Clone Profile")
    cloneBtn.toolTipText = "Duplicate current profile"
    cloneBtn.addActionListener {
        val name = JOptionPane.showInputDialog(frame, "Enter New Profile Name (Clone of ${activeProfile.name}):")
        if (!name.isNullOrBlank()) {
            val clonedLayers = activeProfile.layers.map { layer ->
                layer.copy(
                    m1 = layer.m1.copy(), m2 = layer.m2.copy(), m3 = layer.m3.copy(), m4 = layer.m4.copy(),
                    cmd = layer.cmd.copy(), lib = layer.lib.copy(),
                    m1_m2 = layer.m1_m2.copy(), m1_m3 = layer.m1_m3.copy(), m1_m4 = layer.m1_m4.copy(),
                    m2_m3 = layer.m2_m3.copy(), m2_m4 = layer.m2_m4.copy(), m3_m4 = layer.m3_m4.copy()
                )
            }
            val p = activeProfile.copy(name = name, layers = clonedLayers)
            profiles.add(p)
            saveProfile(p)
            reloadProfileDropdown()
            profileCombo.selectedItem = name
            refreshUI()
        }
    }

    val deleteBtn = JButton("Delete Profile")
    deleteBtn.addActionListener {
        if (profiles.size <= 1) return@addActionListener
        val current = activeProfile
        profiles.remove(current)
        File(rootDir, "${current.name}.properties").delete()
        profileCombo.removeItem(current.name)
        switchActiveProfile(profiles[0].name)
        refreshUI()
    }

    val importBtn = JButton("Import")
    importBtn.addActionListener {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("ShadowLink Profile (*.properties)", "properties") }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                val file = chooser.selectedFile
                val dest = File(rootDir, file.name)
                file.copyTo(dest, overwrite = true)
                profiles.clear()
                loadAllProfiles()
                switchActiveProfile(file.nameWithoutExtension)
                reloadProfileDropdown()
                refreshUI()
                JOptionPane.showMessageDialog(frame, "Profile '${file.nameWithoutExtension}' Imported Successfully!", "Import Success", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(frame, "Failed to import profile:\n${e.message}", "Import Error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    val exportBtn = JButton("Export")
    exportBtn.addActionListener {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export Profile"
            selectedFile = File("${activeProfile.name}.properties")
            fileFilter = FileNameExtensionFilter("ShadowLink Profile (*.properties)", "properties")
        }
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                val source = File(rootDir, "${activeProfile.name}.properties")
                val dest = chooser.selectedFile
                val finalDest = if (dest.name.endsWith(".properties")) dest else File(dest.parentFile, "${dest.name}.properties")
                source.copyTo(finalDest, overwrite = true)
                JOptionPane.showMessageDialog(frame, "Profile Exported Successfully!", "Export Success", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(frame, "Failed to export profile:\n${e.message}", "Export Error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    profileRowLeft.add(JLabel("Current Profile: "))
    profileRowLeft.add(profileCombo)
    profileRowLeft.add(newBtn)
    profileRowLeft.add(cloneBtn)
    profileRowLeft.add(deleteBtn)
    profileRowLeft.add(importBtn)
    profileRowLeft.add(exportBtn)
    profileRowWrapper.add(profileRowLeft, BorderLayout.WEST)

    val profileRowRight = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 5))
    val themeToggleBtn = JToggleButton(if (isDarkMode) "Dark Mode" else "Light Mode", isDarkMode)
    themeToggleBtn.addActionListener {
        isDarkMode = themeToggleBtn.isSelected
        themeToggleBtn.text = if (isDarkMode) "Dark Mode" else "Light Mode"
        try {
            if (isDarkMode) UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf")
            else UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf")
            SwingUtilities.updateComponentTreeUI(frame)
        } catch (e: Exception) {}
    }
    profileRowRight.add(themeToggleBtn)
    profileRowWrapper.add(profileRowRight, BorderLayout.EAST)

    val switchRow = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
    processField = JTextField(activeProfile.targetProcess, 15)
    processField.preferredSize = Dimension(150, 30)

    val browseBtn = JButton("Browse...")
    browseBtn.addActionListener {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Executables (*.exe)", "exe") }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            processField.text = chooser.selectedFile.name.lowercase()
        }
    }

    val activeAppsBtn = JButton("Active Apps...")
    activeAppsBtn.addActionListener { showActiveProcessDialog(frame) }

    val autoSwitchBox = JCheckBox("Auto-Switch Enabled", autoSwitchEnabled)
    autoSwitchBox.addActionListener { autoSwitchEnabled = autoSwitchBox.isSelected }

    switchRow.add(JLabel("Auto-Switch Executable: "))
    switchRow.add(processField)
    switchRow.add(browseBtn)
    switchRow.add(activeAppsBtn)
    switchRow.add(autoSwitchBox)

    topPanel.add(profileRowWrapper)
    topPanel.add(switchRow)
    frame.add(topPanel, BorderLayout.NORTH)

    // --- TABBED PANE FOR LAYERS AND GLOBAL SETTINGS ---
    tabbedPane = JTabbedPane()
    layerUIs.clear()

    // Create 5 Tabs for Layers
    for (i in 0 until 5) {
        val layerPanel = JPanel(BorderLayout())
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 15, 10))
        header.add(JLabel("Layer Name:"))

        val nameField = JTextField(activeProfile.layers[i].name, 15)
        header.add(nameField)

        val enabledBox = JCheckBox("Enable this layer", activeProfile.layers[i].enabled)
        if (i == 0) {
            enabledBox.isSelected = true
            enabledBox.isEnabled = false
        }
        header.add(enabledBox)
        layerPanel.add(header, BorderLayout.NORTH)

        // Two Column Layout
        val center = JPanel(GridLayout(1, 2, 10, 0))

        val leftCol = JPanel(GridLayout(6, 1, 5, 5))
        leftCol.border = BorderFactory.createTitledBorder("Single Buttons")
        val m1C = createPaddleRow(frame, "M1 (Bot-L)", activeProfile.layers[i].m1)
        val m2C = createPaddleRow(frame, "M2 (Top-L)", activeProfile.layers[i].m2)
        val m3C = createPaddleRow(frame, "M3 (Top-R)", activeProfile.layers[i].m3)
        val m4C = createPaddleRow(frame, "M4 (Bot-R)", activeProfile.layers[i].m4)
        val cmdC = createPaddleRow(frame, "Command", activeProfile.layers[i].cmd)
        val libC = createPaddleRow(frame, "Library", activeProfile.layers[i].lib)
        leftCol.add(m1C.panel); leftCol.add(m2C.panel); leftCol.add(m3C.panel)
        leftCol.add(m4C.panel); leftCol.add(cmdC.panel); leftCol.add(libC.panel)

        val rightCol = JPanel(GridLayout(6, 1, 5, 5))
        rightCol.border = BorderFactory.createTitledBorder("Combo Buttons")
        val m1m2C = createPaddleRow(frame, "M1 + M2", activeProfile.layers[i].m1_m2)
        val m1m3C = createPaddleRow(frame, "M1 + M3", activeProfile.layers[i].m1_m3)
        val m1m4C = createPaddleRow(frame, "M1 + M4", activeProfile.layers[i].m1_m4)
        val m2m3C = createPaddleRow(frame, "M2 + M3", activeProfile.layers[i].m2_m3)
        val m2m4C = createPaddleRow(frame, "M2 + M4", activeProfile.layers[i].m2_m4)
        val m3m4C = createPaddleRow(frame, "M3 + M4", activeProfile.layers[i].m3_m4)
        rightCol.add(m1m2C.panel); rightCol.add(m1m3C.panel); rightCol.add(m1m4C.panel)
        rightCol.add(m2m3C.panel); rightCol.add(m2m4C.panel); rightCol.add(m3m4C.panel)

        center.add(leftCol)
        center.add(rightCol)
        layerPanel.add(center, BorderLayout.CENTER)

        val tabTitle = if (i == 0) "Layer 1: ${activeProfile.layers[0].name}" else "Layer ${i+1}: ${activeProfile.layers[i].name}"
        tabbedPane.addTab(tabTitle, layerPanel)

        nameField.document.addDocumentListener(object: DocumentListener {
            fun update() { tabbedPane.setTitleAt(i, "Layer ${i+1}: ${nameField.text}") }
            override fun insertUpdate(e: DocumentEvent?) = update()
            override fun removeUpdate(e: DocumentEvent?) = update()
            override fun changedUpdate(e: DocumentEvent?) = update()
        })

        layerUIs.add(LayerUI(nameField, enabledBox, m1C, m2C, m3C, m4C, cmdC, libC, m1m2C, m1m3C, m1m4C, m2m3C, m2m4C, m3m4C))
    }

    // Tab 6: Layer Settings
    val layerSettingsPanel = JPanel()
    layerSettingsPanel.layout = BoxLayout(layerSettingsPanel, BoxLayout.Y_AXIS)
    layerSettingsPanel.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

    val toggleInfo = JLabel("<html><b>Global Layer Toggle Assignment</b><br>Select which buttons will cycle through your enabled layers. This setting applies across all profiles. Selecting a Single Button toggle will reserve it, but using a Dual Combo allows both buttons to remain active individually!</html>")
    toggleInfo.border = BorderFactory.createEmptyBorder(0, 0, 15, 0)
    layerSettingsPanel.add(toggleInfo)

    val toggleOptions = arrayOf("None", "M1", "M2", "M3", "M4", "Command", "Library")

    val t1Panel = JPanel(FlowLayout(FlowLayout.LEFT))
    t1Panel.add(JLabel("Primary Layer Toggle Button:"))
    t1Combo = JComboBox(toggleOptions).apply { selectedItem = activeProfile.toggleButton1 }
    t1Panel.add(t1Combo)
    layerSettingsPanel.add(t1Panel)

    val t2Panel = JPanel(FlowLayout(FlowLayout.LEFT))
    t2Panel.add(JLabel("Secondary Layer Toggle Button (Optional Double-Input Combo):"))
    t2Combo = JComboBox(toggleOptions).apply { selectedItem = activeProfile.toggleButton2 }
    t2Panel.add(t2Combo)
    layerSettingsPanel.add(t2Panel)

    t1Combo.addActionListener { refreshLayerLocks() }
    t2Combo.addActionListener { refreshLayerLocks() }

    // Advanced Global Settings Section
    layerSettingsPanel.add(Box.createRigidArea(Dimension(0, 20)))
    val advancedInfo = JLabel("<html><b>Advanced Global Settings</b><br><b>Controller Auto-Detect Rate</b> determines how often the app searches for your controller. <i>(Requires app restart)</i><br><b>Combo Input Delay</b> adds a tiny buffer allowing you to trigger combos without misfiring single buttons!</html>")
    advancedInfo.border = BorderFactory.createEmptyBorder(0, 0, 15, 0)
    layerSettingsPanel.add(advancedInfo)

    val advancedPanel = JPanel(GridLayout(2, 1, 5, 5))

    val scanPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    scanPanel.add(JLabel("Controller Auto-Detect Rate:"))
    val scanMap = mapOf(1000 to "1000 ms (Fastest)", 2000 to "2000 ms (Fast)", 5000 to "5000 ms (Default)", 10000 to "10000 ms (Slow)")
    val scanCombo = JComboBox(scanMap.values.toTypedArray())
    scanCombo.selectedItem = scanMap[controllerScanInterval] ?: scanMap[5000]
    scanCombo.addActionListener {
        val selectedText = scanCombo.selectedItem as String
        controllerScanInterval = scanMap.entries.firstOrNull { it.value == selectedText }?.key ?: 5000
    }
    scanPanel.add(scanCombo)
    advancedPanel.add(scanPanel)

    val bufferPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    bufferPanel.add(JLabel("Combo Input Delay (Microlag Buffer):"))
    val bufferSpinner = JSpinner(SpinnerNumberModel(comboBufferMs, 0, 500, 5))
    bufferSpinner.addChangeListener {
        comboBufferMs = bufferSpinner.value as Int
    }
    bufferPanel.add(bufferSpinner)
    bufferPanel.add(JLabel("ms (0 = Instant/No Buffer, 30 = Recommended)"))
    advancedPanel.add(bufferPanel)

    layerSettingsPanel.add(advancedPanel)

    tabbedPane.addTab("Controller/Layer Settings", layerSettingsPanel)

    frame.add(tabbedPane, BorderLayout.CENTER)

    val bottomPanel = JPanel(BorderLayout())
    val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 15, 10))

    val minimizedBox = JCheckBox("Start Minimized", startMinimized)
    minimizedBox.addActionListener { startMinimized = minimizedBox.isSelected }
    optionsPanel.add(minimizedBox)

    val startupBox = JCheckBox("Load on Startup", loadOnStartup)
    startupBox.addActionListener { loadOnStartup = startupBox.isSelected }
    optionsPanel.add(startupBox)

    val osdPositions = arrayOf("Bottom Right", "Bottom Left", "Top Right", "Top Left")
    val osdCombo = JComboBox(osdPositions).apply { selectedItem = osdPosition }
    osdCombo.addActionListener { osdPosition = osdCombo.selectedItem as String }
    optionsPanel.add(JLabel("OSD Corner:"))
    optionsPanel.add(osdCombo)

    val helpBtn = JButton("Macro Help")
    helpBtn.addActionListener { showMacroInstructions(frame) }
    optionsPanel.add(helpBtn)

    val layerHelpBtn = JButton("Layer Help")
    layerHelpBtn.addActionListener { showLayerInstructions(frame) }
    optionsPanel.add(layerHelpBtn)

    val updateBtn = JButton("Check for Updates")
    updateBtn.addActionListener { checkForUpdates(frame) }
    optionsPanel.add(updateBtn)

    bottomPanel.add(optionsPanel, BorderLayout.WEST)

    val saveBtn = JButton("Save & Apply Settings")
    saveBtn.font = Font("Segoe UI", Font.BOLD, 16)
    saveBtn.addActionListener {
        updateActiveProfileFromUI()
        saveProfile(activeProfile)
        saveGlobalConfig()
        updateStartupRegistry(loadOnStartup)
        JOptionPane.showMessageDialog(frame, "Settings & Profile '${activeProfile.name}' Saved!")
    }
    bottomPanel.add(saveBtn, BorderLayout.EAST)
    frame.add(bottomPanel, BorderLayout.SOUTH)

    refreshLayerLocks()
    setupSystemTray(frame)
}

// --- UPDATER ---
fun checkForUpdates(parent: JFrame) {
    Thread {
        try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            val connection = URI(apiUrl).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode != 200) {
                SwingUtilities.invokeLater { JOptionPane.showMessageDialog(parent, "Could not check for updates. GitHub API returned: ${connection.responseCode}") }
                return@Thread
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val tagMatch = "\"tag_name\":\\s*\"v?([^\"]+)\"".toRegex().find(response)
            val latestVersion = tagMatch?.groups?.get(1)?.value

            if (latestVersion == null) {
                SwingUtilities.invokeLater { JOptionPane.showMessageDialog(parent, "Failed to parse version from GitHub.") }
                return@Thread
            }

            if (latestVersion <= APP_VERSION) {
                SwingUtilities.invokeLater { JOptionPane.showMessageDialog(parent, "You are up to date! (Version $APP_VERSION)") }
                return@Thread
            }

            val currentFile = File(PaddleBind::class.java.protectionDomain.codeSource.location.toURI())
            val extension = if (currentFile.name.endsWith(".jar", true)) ".jar" else ".exe"

            val urlMatch = "\"browser_download_url\":\\s*\"([^\"]+\\$extension)\"".toRegex().find(response)
            val downloadUrl = urlMatch?.groups?.get(1)?.value

            if (downloadUrl == null) {
                SwingUtilities.invokeLater { JOptionPane.showMessageDialog(parent, "Update found (v$latestVersion), but no $extension asset was found.") }
                return@Thread
            }

            val choice = JOptionPane.showConfirmDialog(parent, "Version $latestVersion is available!\n\nWould you like to download and install it now?", "Update Available", JOptionPane.YES_NO_OPTION)
            if (choice != JOptionPane.YES_OPTION) return@Thread

            val updateFile = File(currentFile.parentFile, "ShadowLink_Update$extension")
            URI(downloadUrl).toURL().openStream().use { input ->
                FileOutputStream(updateFile).use { output ->
                    input.copyTo(output)
                }
            }

            val batFile = File(currentFile.parentFile, "updater.bat")
            val batContent = """
                @echo off
                cd /d "%~dp0"
                timeout /t 2 /nobreak > nul
                del "${currentFile.name}"
                ren "${updateFile.name}" "${currentFile.name}"
                start "" "${currentFile.name}"
                del "%~f0" & exit
            """.trimIndent()
            batFile.writeText(batContent)

            val pb = ProcessBuilder("cmd", "/c", "start", "", batFile.absolutePath)
            pb.directory(currentFile.parentFile)
            pb.start()
            System.exit(0)

        } catch (e: Exception) {
            e.printStackTrace()
            SwingUtilities.invokeLater { JOptionPane.showMessageDialog(parent, "Error checking for updates: ${e.message}") }
        }
    }.start()
}

// --- PROCESS PICKER DIALOG ---
fun showActiveProcessDialog(parent: JFrame) {
    val dialog = JDialog(parent, "Select Running App", true)
    dialog.layout = BorderLayout(10, 10)
    dialog.setSize(500, 550)
    dialog.setLocationRelativeTo(parent)

    val topContainer = JPanel(BorderLayout(5, 5))
    topContainer.border = BorderFactory.createEmptyBorder(15, 15, 5, 15)

    topContainer.add(JLabel("Search for an open app:"), BorderLayout.NORTH)

    val searchField = JTextField()
    topContainer.add(searchField, BorderLayout.CENTER)
    dialog.add(topContainer, BorderLayout.NORTH)

    val processMap = mutableMapOf<String, String>()
    User32.INSTANCE.EnumWindows({ hwnd, _ ->
        if (User32.INSTANCE.IsWindowVisible(hwnd)) {
            val titleLength = User32.INSTANCE.GetWindowTextLength(hwnd)
            if (titleLength > 0) {
                val titleArr = CharArray(titleLength + 1)
                User32.INSTANCE.GetWindowText(hwnd, titleArr, titleLength + 1)
                val title = String(titleArr).trim { it <= ' ' || it == '\u0000' }

                val exeName = getProcessNameFromHwnd(hwnd)
                if (exeName.isNotEmpty() && !exeName.equals("explorer.exe", true)) {
                    processMap[exeName] = title
                }
            }
        }
        true
    }, null)

    val sortedKeys = processMap.keys.sortedBy { it.lowercase() }
    val listModel = DefaultListModel<String>()
    sortedKeys.forEach { listModel.addElement(it) }

    val list = JList(listModel)
    list.font = Font("Segoe UI", Font.PLAIN, 14)
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION

    list.cellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(l: JList<*>?, v: Any?, i: Int, s: Boolean, f: Boolean): Component {
            val comp = super.getListCellRendererComponent(l, v, i, s, f) as JLabel
            val name = v as String
            comp.text = "<html><b>$name</b> <font color='gray'>(${processMap[name]})</font></html>"
            comp.border = BorderFactory.createEmptyBorder(2, 5, 2, 5)
            return comp
        }
    }

    searchField.document.addDocumentListener(object : DocumentListener {
        fun update() {
            val query = searchField.text.lowercase()
            listModel.clear()
            sortedKeys.filter { it.lowercase().contains(query) || (processMap[it]?.lowercase()?.contains(query) == true) }
                .forEach { listModel.addElement(it) }
        }
        override fun insertUpdate(e: DocumentEvent?) = update()
        override fun removeUpdate(e: DocumentEvent?) = update()
        override fun changedUpdate(e: DocumentEvent?) = update()
    })

    val scroll = JScrollPane(list)
    scroll.border = BorderFactory.createEmptyBorder(5, 15, 10, 15)
    dialog.add(scroll, BorderLayout.CENTER)

    val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 15, 10))
    val selectBtn = JButton("Use Selected Process")
    selectBtn.addActionListener {
        val selected = list.selectedValue
        if (selected != null) {
            processField.text = selected.lowercase()
            dialog.dispose()
        }
    }
    btnPanel.add(selectBtn)
    dialog.add(btnPanel, BorderLayout.SOUTH)

    dialog.isVisible = true
}

// --- MACRO RECORDER DIALOG ---
fun openMacroRecorder(parent: JFrame, targetField: JTextField) {
    val d = JDialog(parent, "Live Macro Recorder", true)
    d.setSize(600, 450)
    d.setLocationRelativeTo(parent)
    d.layout = BorderLayout(10, 10)

    val textArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        text = targetField.text
        font = Font("Monospaced", Font.PLAIN, 14)
    }

    val textScroll = JScrollPane(textArea).apply {
        border = BorderFactory.createTitledBorder("Recorded Macro String:")
    }

    var recording = false
    var lastTime = 0L
    val tokens = mutableListOf<String>()
    if (textArea.text.isNotBlank()) {
        tokens.addAll(textArea.text.split(",").map { it.trim() })
    }

    fun addToken(t: String) {
        if (!recording) return
        val now = System.currentTimeMillis()
        if (lastTime > 0L) {
            val delay = now - lastTime
            if (delay > 10) tokens.add(delay.toString())
        }
        tokens.add(t)
        lastTime = now
        SwingUtilities.invokeLater { textArea.text = tokens.joinToString(", ") }
    }

    val capturePanel = object : JPanel() {
        init {
            isFocusable = true
            background = UIManager.getColor("Panel.background")
            border = BorderFactory.createTitledBorder("Click here to focus, then perform your Macro...")
            preferredSize = Dimension(450, 80)

            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    val mapped = KEY_MAP.entries.find { it.value == e.keyCode }?.key ?: return
                    addToken("$mapped down")
                }
                override fun keyReleased(e: KeyEvent) {
                    val mapped = KEY_MAP.entries.find { it.value == e.keyCode }?.key ?: return
                    addToken("$mapped up")
                }
            })

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val btn = when(e.button) { 1 -> "LClick down"; 2 -> "MClick down"; 3 -> "RClick down"; else -> return }
                    addToken(btn)
                }
                override fun mouseReleased(e: MouseEvent) {
                    val btn = when(e.button) { 1 -> "LClick up"; 2 -> "MClick up"; 3 -> "RClick up"; else -> return }
                    addToken(btn)
                }
            })
        }
    }

    val btnPanel = JPanel(FlowLayout())
    val startBtn = JButton("Start Recording")
    val stopBtn = JButton("Stop").apply { isEnabled = false }
    val clearBtn = JButton("Clear")
    val saveBtn = JButton("Save to Macro")

    startBtn.addActionListener {
        recording = true
        lastTime = 0L
        startBtn.isEnabled = false
        stopBtn.isEnabled = true
        capturePanel.requestFocusInWindow()
        capturePanel.background = Color(255, 200, 200)
    }

    stopBtn.addActionListener {
        recording = false
        lastTime = 0L
        startBtn.isEnabled = true
        stopBtn.isEnabled = false
        capturePanel.background = UIManager.getColor("Panel.background")
    }

    clearBtn.addActionListener {
        tokens.clear()
        textArea.text = ""
    }

    saveBtn.addActionListener {
        targetField.text = tokens.joinToString(", ")
        d.dispose()
    }

    btnPanel.add(startBtn); btnPanel.add(stopBtn); btnPanel.add(clearBtn); btnPanel.add(saveBtn)

    val bottomContainer = JPanel(BorderLayout())
    bottomContainer.add(capturePanel, BorderLayout.CENTER)
    bottomContainer.add(btnPanel, BorderLayout.SOUTH)

    d.add(textScroll, BorderLayout.CENTER)
    d.add(bottomContainer, BorderLayout.SOUTH)

    d.isVisible = true
}

// --- LOGIC ---

fun getProcessNameFromHwnd(hwnd: HWND): String {
    val processId = IntByReference()
    User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId)
    val processHandle = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_INFORMATION or WinNT.PROCESS_VM_READ, false, processId.value)
    if (processHandle != null) {
        val buffer = CharArray(1024)
        val size = IntByReference(buffer.size)
        val success = Kernel32.INSTANCE.QueryFullProcessImageName(processHandle, 0, buffer, size)
        Kernel32.INSTANCE.CloseHandle(processHandle)
        if (success) return File(String(buffer, 0, size.value)).name
    }
    return ""
}

fun getForegroundProcessName(): String {
    val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return ""
    return getProcessNameFromHwnd(hwnd)
}

fun switchActiveProfile(name: String) {
    val found = profiles.find { it.name == name }
    if (found != null) {
        activeProfile = found
        activeLayer = 1
        println("Switched to profile: ${found.name}")
    }
}

fun refreshUI() {
    processField.text = activeProfile.targetProcess
    profileCombo.selectedItem = activeProfile.name
    t1Combo.selectedItem = activeProfile.toggleButton1
    t2Combo.selectedItem = activeProfile.toggleButton2

    for (i in 0 until 5) {
        val config = activeProfile.layers[i]
        val ui = layerUIs[i]

        ui.nameField.text = config.name
        ui.enabledBox.isSelected = config.enabled

        refreshPaddleRow(ui.m1, config.m1)
        refreshPaddleRow(ui.m2, config.m2)
        refreshPaddleRow(ui.m3, config.m3)
        refreshPaddleRow(ui.m4, config.m4)
        refreshPaddleRow(ui.cmd, config.cmd)
        refreshPaddleRow(ui.lib, config.lib)

        refreshPaddleRow(ui.m1m2, config.m1_m2)
        refreshPaddleRow(ui.m1m3, config.m1_m3)
        refreshPaddleRow(ui.m1m4, config.m1_m4)
        refreshPaddleRow(ui.m2m3, config.m2_m3)
        refreshPaddleRow(ui.m2m4, config.m2_m4)
        refreshPaddleRow(ui.m3m4, config.m3_m4)
    }
    refreshLayerLocks()
}

fun updateActiveProfileFromUI() {
    activeProfile.targetProcess = processField.text.trim().lowercase()
    activeProfile.toggleButton1 = t1Combo.selectedItem as String
    activeProfile.toggleButton2 = t2Combo.selectedItem as String

    for (i in 0 until 5) {
        val config = activeProfile.layers[i]
        val ui = layerUIs[i]

        config.name = ui.nameField.text.trim()
        config.enabled = ui.enabledBox.isSelected

        updateBindFromUI(config.m1, ui.m1)
        updateBindFromUI(config.m2, ui.m2)
        updateBindFromUI(config.m3, ui.m3)
        updateBindFromUI(config.m4, ui.m4)
        updateBindFromUI(config.cmd, ui.cmd)
        updateBindFromUI(config.lib, ui.lib)

        updateBindFromUI(config.m1_m2, ui.m1m2)
        updateBindFromUI(config.m1_m3, ui.m1m3)
        updateBindFromUI(config.m1_m4, ui.m1m4)
        updateBindFromUI(config.m2_m3, ui.m2m3)
        updateBindFromUI(config.m2_m4, ui.m2m4)
        updateBindFromUI(config.m3_m4, ui.m3m4)
    }
}

fun runAutoSwitchWatchdog() {
    while (true) {
        if (autoSwitchEnabled) {
            val activeProcess = getForegroundProcessName().lowercase()
            if (activeProcess.isNotEmpty()) {
                val matchingProfile = profiles.find { it.targetProcess.isNotEmpty() && it.targetProcess == activeProcess }

                if (matchingProfile != null && matchingProfile.name != activeProfile.name) {
                    switchActiveProfile(matchingProfile.name)
                    SwingUtilities.invokeLater { refreshUI() }
                }
            }
        }
        Thread.sleep(1500)
    }
}

// --- SNIFFER ---

class ButtonState(
    @Volatile var pressed: Boolean = false,
    @Volatile var macroThread: Thread? = null,
    @Volatile var stepIndex: Int = 0,
    @Volatile var activeBind: PaddleBind? = null,
    @Volatile var singleActionFired: Boolean = false,
    @Volatile var comboConsumed: Boolean = false, // Prevents roll-off misfires
    var pendingTask: java.util.TimerTask? = null
) {
    @Synchronized
    fun cancelPending() {
        pendingTask?.cancel()
        pendingTask = null
    }

    @Synchronized
    fun fire(robot: Robot, bind: PaddleBind) {
        macroThread?.interrupt()
        activeBind = bind
        if (bind.isMacro) {
            if (bind.stepThrough) executeMacroStep(robot, bind, this)
            else macroThread = executeMacro(robot, bind, this)
        } else {
            pressKeyBind(robot, bind)
        }
    }

    @Synchronized
    fun releaseIfActive(robot: Robot) {
        val releaseTarget = this.activeBind
        if (releaseTarget != null && releaseTarget.enabled) {
            if (releaseTarget.isMacro) {
                if (releaseTarget.repeatMacro) {
                    this.macroThread = null // Detaches repeat thread
                }
            } else {
                releaseKeyBind(robot, releaseTarget)
            }
        }
        this.activeBind = null
    }

    @Synchronized
    fun cancelAndRelease(robot: Robot) {
        cancelPending()
        releaseIfActive(robot)
        pressed = false
    }
}

fun runControllerSniffer() {
    val spec = HidServicesSpecification().apply { scanInterval = controllerScanInterval }
    val hidServices = HidManager.getHidServices(spec)
    val robot = Robot().apply { isAutoWaitForIdle = false }

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
                readingThread = Thread {
                    val actionTimer = java.util.Timer("ShadowLink-ActionTimer", true)

                    val m1State = ButtonState(); val m2State = ButtonState()
                    val m3State = ButtonState(); val m4State = ButtonState()
                    val cmdState = ButtonState(); val libState = ButtonState()

                    val m1m2State = ButtonState(); val m1m3State = ButtonState()
                    val m1m4State = ButtonState(); val m2m3State = ButtonState()
                    val m2m4State = ButtonState(); val m3m4State = ButtonState()

                    var wasToggleTriggered = false

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

                                    val states = mapOf("M1" to s1, "M2" to s2, "M3" to s3, "M4" to s4, "Command" to sCmd, "Library" to sLib)

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

                                        // Preemptively cancel and release active binds before shifting
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

                                    // Suspend inputs used by global layer toggle
                                    if (isComboTriggered) {
                                        if (t1 != "None") consumed.add(t1)
                                        if (t2 != "None") consumed.add(t2)
                                    } else if (isSingleToggle && singleToggleBtn != "None") {
                                        // Single toggle gets permanently suspended from individual action use
                                        consumed.add(singleToggleBtn)
                                    }

                                    val currentLayerConfig = p.layers[activeLayer - 1]

                                    fun handleCombo(name1: String, sA: Boolean, name2: String, sB: Boolean, bs: ButtonState, bind: PaddleBind, stateA: ButtonState, stateB: ButtonState) {
                                        if (sA && sB && bind.enabled && !consumed.contains(name1) && !consumed.contains(name2)) {
                                            consumed.add(name1)
                                            consumed.add(name2)
                                            if (!bs.pressed) {
                                                // Cancel pending singles before firing combo
                                                stateA.cancelPending()
                                                stateB.cancelPending()

                                                // If singles fired before we could catch the combo, roll them off cleanly
                                                if (stateA.singleActionFired) stateA.releaseIfActive(robot)
                                                if (stateB.singleActionFired) stateB.releaseIfActive(robot)

                                                // Flag singles as consumed so they don't fire rapidly on release
                                                stateA.comboConsumed = true
                                                stateB.comboConsumed = true

                                                bs.pressed = true
                                                bs.singleActionFired = true
                                                bs.fire(robot, bind)
                                            }
                                        } else {
                                            if (bs.pressed) {
                                                bs.releaseIfActive(robot)
                                                bs.pressed = false
                                            }
                                        }
                                    }

                                    // Evaluate Combo Binds first
                                    handleCombo("M1", s1, "M2", s2, m1m2State, currentLayerConfig.m1_m2, m1State, m2State)
                                    handleCombo("M1", s1, "M3", s3, m1m3State, currentLayerConfig.m1_m3, m1State, m3State)
                                    handleCombo("M1", s1, "M4", s4, m1m4State, currentLayerConfig.m1_m4, m1State, m4State)
                                    handleCombo("M2", s2, "M3", s3, m2m3State, currentLayerConfig.m2_m3, m2State, m3State)
                                    handleCombo("M2", s2, "M4", s4, m2m4State, currentLayerConfig.m2_m4, m2State, m4State)
                                    handleCombo("M3", s3, "M4", s4, m3m4State, currentLayerConfig.m3_m4, m3State, m4State)

                                    // Evaluate Single Binds
                                    fun handleSingle(name: String, state: Boolean, bs: ButtonState, bind: PaddleBind) {
                                        if (consumed.contains(name)) {
                                            if (!bs.pressed) bs.pressed = true // Hardware is held
                                            bs.cancelPending()
                                            if (bs.singleActionFired) {
                                                bs.releaseIfActive(robot)
                                                bs.singleActionFired = false
                                            }
                                            bs.comboConsumed = true
                                            return
                                        }

                                        if (state && !bs.pressed) {
                                            bs.pressed = true
                                            bs.singleActionFired = false
                                            bs.comboConsumed = false

                                            if (bind.enabled) {
                                                if (comboBufferMs > 0) {
                                                    bs.pendingTask = object : java.util.TimerTask() {
                                                        override fun run() {
                                                            if (!bs.comboConsumed) {
                                                                bs.singleActionFired = true
                                                                bs.fire(robot, bind)
                                                            }
                                                        }
                                                    }
                                                    actionTimer.schedule(bs.pendingTask, comboBufferMs.toLong())
                                                } else {
                                                    bs.singleActionFired = true
                                                    bs.fire(robot, bind)
                                                }
                                            }
                                        } else if (!state && bs.pressed) {
                                            bs.cancelPending()
                                            if (bind.enabled && !bs.singleActionFired && !bs.comboConsumed) {
                                                // Key released before buffer expired (a rapid tap), fire it now
                                                bs.fire(robot, bind)
                                            }
                                            bs.releaseIfActive(robot)
                                            bs.pressed = false
                                            bs.comboConsumed = false
                                        }
                                    }

                                    handleSingle("M1", s1, m1State, currentLayerConfig.m1)
                                    handleSingle("M2", s2, m2State, currentLayerConfig.m2)
                                    handleSingle("M3", s3, m3State, currentLayerConfig.m3)
                                    handleSingle("M4", s4, m4State, currentLayerConfig.m4)
                                    handleSingle("Command", sCmd, cmdState, currentLayerConfig.cmd)
                                    handleSingle("Library", sLib, libState, currentLayerConfig.lib)

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

// --- PERSISTENCE & SYSTEM ---

fun loadAllProfiles() {
    val files = rootDir.listFiles { _, name -> name.endsWith(".properties") }
    if (files.isNullOrEmpty()) {
        val default = Profile(name = "Default")
        profiles.add(default)
        activeProfile = default
        saveProfile(default)
    } else {
        files.forEach { file ->
            val props = Properties()
            FileInputStream(file).use { props.load(it) }
            val p = Profile(name = file.nameWithoutExtension)
            p.targetProcess = props.getProperty("TARGET_PROCESS", "")
            p.toggleButton1 = props.getProperty("TOGGLE_BTN_1", "None")
            p.toggleButton2 = props.getProperty("TOGGLE_BTN_2", "None")

            // Legacy single toggle check
            if (p.toggleButton1 == "None") {
                if (props.getProperty("M1_LT", "false").toBoolean()) p.toggleButton1 = "M1"
                else if (props.getProperty("M2_LT", "false").toBoolean()) p.toggleButton1 = "M2"
                else if (props.getProperty("M3_LT", "false").toBoolean()) p.toggleButton1 = "M3"
                else if (props.getProperty("M4_LT", "false").toBoolean()) p.toggleButton1 = "M4"
                else if (props.getProperty("CMD_LT", "false").toBoolean()) p.toggleButton1 = "Command"
                else if (props.getProperty("LIB_LT", "false").toBoolean()) p.toggleButton1 = "Library"
            }

            fun loadB(prefix: String, b: PaddleBind) {
                b.enabled = props.getProperty("${prefix}_EN", if(prefix.contains("M") && prefix.length > 3) "false" else "true").toBoolean()
                b.isMacro = props.getProperty("${prefix}_MAC", "false").toBoolean()
                b.repeatMacro = props.getProperty("${prefix}_REP", "false").toBoolean()
                b.stepThrough = props.getProperty("${prefix}_STP", "false").toBoolean()
                b.macroText = props.getProperty("${prefix}_TXT", "")
                b.keyChar = props.getProperty("${prefix}_KEY", "A")
                b.shift = props.getProperty("${prefix}_SH", "false").toBoolean()
                b.ctrl = props.getProperty("${prefix}_CT", "false").toBoolean()
                b.alt = props.getProperty("${prefix}_AL", "false").toBoolean()
                b.win = props.getProperty("${prefix}_WI", "false").toBoolean()
            }

            for (i in 0 until 5) {
                val suffix = if (i == 0) "" else "_L${i+1}"
                p.layers[i].name = props.getProperty("LAYER${i+1}_NAME", "Layer ${i+1}")
                p.layers[i].enabled = props.getProperty("LAYER${i+1}_EN", if (i == 0) "true" else "false").toBoolean()

                loadB("M1$suffix", p.layers[i].m1)
                loadB("M2$suffix", p.layers[i].m2)
                loadB("M3$suffix", p.layers[i].m3)
                loadB("M4$suffix", p.layers[i].m4)
                loadB("CMD$suffix", p.layers[i].cmd)
                loadB("LIB$suffix", p.layers[i].lib)

                loadB("M1M2$suffix", p.layers[i].m1_m2)
                loadB("M1M3$suffix", p.layers[i].m1_m3)
                loadB("M1M4$suffix", p.layers[i].m1_m4)
                loadB("M2M3$suffix", p.layers[i].m2_m3)
                loadB("M2M4$suffix", p.layers[i].m2_m4)
                loadB("M3M4$suffix", p.layers[i].m3_m4)
            }

            profiles.add(p)
        }

        if (globalConfigFile.exists()) {
            val global = Properties()
            FileInputStream(globalConfigFile).use { global.load(it) }
            val lastActive = global.getProperty("ACTIVE_PROFILE", "Default")
            autoSwitchEnabled = global.getProperty("AUTO_SWITCH", "true").toBoolean()
            startMinimized = global.getProperty("START_MINIMIZED", "false").toBoolean()
            loadOnStartup = global.getProperty("LOAD_ON_STARTUP", "false").toBoolean()
            isDarkMode = global.getProperty("DARK_MODE", "true").toBoolean()
            osdPosition = global.getProperty("OSD_POSITION", "Bottom Right")
            controllerScanInterval = global.getProperty("SCAN_INTERVAL", "5000").toIntOrNull() ?: 5000
            comboBufferMs = global.getProperty("COMBO_BUFFER_MS", "30").toIntOrNull() ?: 30

            activeProfile = profiles.find { it.name == lastActive } ?: profiles[0]
        } else {
            activeProfile = profiles[0]
        }
    }
}

fun saveProfile(p: Profile) {
    val props = Properties()
    props.setProperty("TARGET_PROCESS", p.targetProcess)
    props.setProperty("TOGGLE_BTN_1", p.toggleButton1)
    props.setProperty("TOGGLE_BTN_2", p.toggleButton2)

    fun saveB(prefix: String, b: PaddleBind) {
        props.setProperty("${prefix}_EN", b.enabled.toString())
        props.setProperty("${prefix}_MAC", b.isMacro.toString())
        props.setProperty("${prefix}_REP", b.repeatMacro.toString())
        props.setProperty("${prefix}_STP", b.stepThrough.toString())
        props.setProperty("${prefix}_TXT", b.macroText)
        props.setProperty("${prefix}_KEY", b.keyChar)
        props.setProperty("${prefix}_SH", b.shift.toString())
        props.setProperty("${prefix}_CT", b.ctrl.toString())
        props.setProperty("${prefix}_AL", b.alt.toString())
        props.setProperty("${prefix}_WI", b.win.toString())
    }

    for (i in 0 until 5) {
        val suffix = if (i == 0) "" else "_L${i+1}"
        props.setProperty("LAYER${i+1}_NAME", p.layers[i].name)
        props.setProperty("LAYER${i+1}_EN", p.layers[i].enabled.toString())

        saveB("M1$suffix", p.layers[i].m1); saveB("M2$suffix", p.layers[i].m2); saveB("M3$suffix", p.layers[i].m3)
        saveB("M4$suffix", p.layers[i].m4); saveB("CMD$suffix", p.layers[i].cmd); saveB("LIB$suffix", p.layers[i].lib)

        saveB("M1M2$suffix", p.layers[i].m1_m2); saveB("M1M3$suffix", p.layers[i].m1_m3); saveB("M1M4$suffix", p.layers[i].m1_m4)
        saveB("M2M3$suffix", p.layers[i].m2_m3); saveB("M2M4$suffix", p.layers[i].m2_m4); saveB("M3M4$suffix", p.layers[i].m3_m4)
    }

    FileOutputStream(File(rootDir, "${p.name}.properties")).use { props.store(it, null) }
}

fun saveGlobalConfig() {
    val props = Properties()
    props.setProperty("ACTIVE_PROFILE", activeProfile.name)
    props.setProperty("AUTO_SWITCH", autoSwitchEnabled.toString())
    props.setProperty("START_MINIMIZED", startMinimized.toString())
    props.setProperty("LOAD_ON_STARTUP", loadOnStartup.toString())
    props.setProperty("DARK_MODE", isDarkMode.toString())
    props.setProperty("OSD_POSITION", osdPosition)
    props.setProperty("SCAN_INTERVAL", controllerScanInterval.toString())
    props.setProperty("COMBO_BUFFER_MS", comboBufferMs.toString())

    FileOutputStream(globalConfigFile).use { props.store(it, null) }
}

fun updateStartupRegistry(enable: Boolean) {
    try {
        val appName = "ShadowLink"
        val path = File(PaddleBind::class.java.protectionDomain.codeSource.location.toURI()).absolutePath

        if (path.contains("classes") || path.contains("out") || path.contains("build")) {
            println("Running from IDE, ignoring startup setting.")
            return
        }

        val domain = System.getenv("USERDOMAIN") ?: System.getenv("COMPUTERNAME") ?: ""
        val user = System.getenv("USERNAME") ?: System.getProperty("user.name") ?: ""
        val currentUser = if (domain.isNotEmpty()) "$domain\\$user" else user

        val psScriptText = if (enable) {
            val isJar = path.lowercase().endsWith(".jar")
            val targetExe = if (isJar) File(System.getProperty("java.home"), "bin\\javaw.exe").absolutePath else path
            val args = if (isJar) "-jar \"$path\"" else ""
            val workingDir = File(path).parentFile.absolutePath

            """
                try {
                    ${'$'}Action = New-ScheduledTaskAction -Execute '$targetExe' ${if (args.isNotEmpty()) "-Argument '$args'" else ""} -WorkingDirectory '$workingDir'
                    ${'$'}Trigger = New-ScheduledTaskTrigger -AtLogOn
                    ${'$'}Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit 0
                    ${'$'}Principal = New-ScheduledTaskPrincipal -UserId '$currentUser' -LogonType Interactive -RunLevel Highest
                    Register-ScheduledTask -TaskName '$appName' -Action ${'$'}Action -Trigger ${'$'}Trigger -Settings ${'$'}Settings -Principal ${'$'}Principal -Force
                } catch {}
            """.trimIndent()
        } else {
            """
                try {
                    Unregister-ScheduledTask -TaskName '$appName' -Confirm:${'$'}false
                } catch {}
            """.trimIndent()
        }

        val tempScript = File.createTempFile("ShadowLink_Startup", ".ps1")
        tempScript.writeText(psScriptText)

        val uacCommand = "Start-Process powershell -ArgumentList '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"${tempScript.absolutePath}\"' -Verb RunAs -Wait"
        val outerBase64 = java.util.Base64.getEncoder().encodeToString(uacCommand.toByteArray(Charsets.UTF_16LE))

        Runtime.getRuntime().exec(arrayOf("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", outerBase64)).waitFor()
        tempScript.delete()

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// --- UI HELPERS ---

class PaddleUIControls(
    val panel: JPanel, val enabledBox: JCheckBox, val isMacroBox: JCheckBox,
    val repeatMacroBox: JCheckBox, val stepThroughBox: JCheckBox, val macroField: JTextField,
    val shiftBox: JCheckBox, val ctrlBox: JCheckBox,
    val altBox: JCheckBox, val winBox: JCheckBox, val keyDropdown: JComboBox<String>,
    val reservedLabel: JLabel, val recordBtn: JButton
) {
    var isReserved: Boolean = false

    fun refreshVis() {
        val e = enabledBox.isSelected; val m = isMacroBox.isSelected

        enabledBox.isVisible = !isReserved
        isMacroBox.isVisible = !isReserved

        isMacroBox.isEnabled = e
        macroField.isEnabled = e && m
        recordBtn.isEnabled = e && m

        if (repeatMacroBox.isSelected) stepThroughBox.isSelected = false
        if (stepThroughBox.isSelected) repeatMacroBox.isSelected = false

        repeatMacroBox.isEnabled = e && m
        stepThroughBox.isEnabled = e && m

        keyDropdown.isEnabled = e && !m
        shiftBox.isEnabled = e && !m; ctrlBox.isEnabled = e && !m
        altBox.isEnabled = e && !m; winBox.isEnabled = e && !m

        macroField.isVisible = m && !isReserved; repeatMacroBox.isVisible = m && !isReserved
        stepThroughBox.isVisible = m && !isReserved; recordBtn.isVisible = m && !isReserved

        keyDropdown.isVisible = !m && !isReserved; shiftBox.isVisible = !m && !isReserved; ctrlBox.isVisible = !m && !isReserved
        altBox.isVisible = !m && !isReserved; winBox.isVisible = !m && !isReserved

        reservedLabel.isVisible = isReserved
        panel.revalidate()
        panel.repaint()
    }
}

fun createPaddleRow(parentFrame: JFrame, name: String, bind: PaddleBind): PaddleUIControls {
    val panel = JPanel(FlowLayout(FlowLayout.CENTER, 5, 5))
    panel.add(JLabel("$name: "))

    val en = JCheckBox("Enabled", bind.enabled).apply { margin = Insets(0, 0, 0, 0) }
    val mac = JCheckBox("Macro", bind.isMacro).apply { margin = Insets(0, 0, 0, 0) }
    val rep = JCheckBox("Repeat", bind.repeatMacro).apply { margin = Insets(0, 0, 0, 0) }
    val stp = JCheckBox("Step", bind.stepThrough).apply { margin = Insets(0, 0, 0, 0) }
    val txt = JTextField(bind.macroText, 12)

    val sh = JCheckBox("Shift", bind.shift).apply { margin = Insets(0, 0, 0, 0) }
    val ct = JCheckBox("Ctrl", bind.ctrl).apply { margin = Insets(0, 0, 0, 0) }
    val al = JCheckBox("Alt", bind.alt).apply { margin = Insets(0, 0, 0, 0) }
    val wi = JCheckBox("Win", bind.win).apply { margin = Insets(0, 0, 0, 0) }

    val key = JComboBox(SUPPORTED_KEYS).apply { selectedItem = bind.keyChar }

    val resLbl = JLabel("Reserved for Layer Toggle").apply {
        foreground = Color.GRAY
        font = font.deriveFont(Font.ITALIC)
        isVisible = false
    }

    val recBtn = JButton("⏺").apply {
        foreground = Color.RED
        margin = Insets(2, 4, 2, 4)
        toolTipText = "Open Macro Recorder"
        addActionListener { openMacroRecorder(parentFrame, txt) }
    }

    val uiControls = PaddleUIControls(panel, en, mac, rep, stp, txt, sh, ct, al, wi, key, resLbl, recBtn)

    en.addActionListener { uiControls.refreshVis() }
    mac.addActionListener { uiControls.refreshVis() }
    rep.addActionListener { if (rep.isSelected) stp.isSelected = false; uiControls.refreshVis() }
    stp.addActionListener { if (stp.isSelected) rep.isSelected = false; uiControls.refreshVis() }

    panel.add(en); panel.add(mac); panel.add(rep); panel.add(stp)
    panel.add(sh); panel.add(ct); panel.add(al); panel.add(wi); panel.add(key)
    panel.add(recBtn); panel.add(txt); panel.add(resLbl)

    uiControls.refreshVis()
    return uiControls
}

fun refreshPaddleRow(c: PaddleUIControls, b: PaddleBind) {
    c.enabledBox.isSelected = b.enabled
    c.isMacroBox.isSelected = b.isMacro
    c.repeatMacroBox.isSelected = b.repeatMacro
    c.stepThroughBox.isSelected = b.stepThrough
    c.macroField.text = b.macroText
    c.shiftBox.isSelected = b.shift
    c.ctrlBox.isSelected = b.ctrl
    c.altBox.isSelected = b.alt
    c.winBox.isSelected = b.win
    c.keyDropdown.selectedItem = b.keyChar
    c.refreshVis()
}

fun updateBindFromUI(b: PaddleBind, c: PaddleUIControls) {
    b.enabled = c.enabledBox.isSelected
    b.isMacro = c.isMacroBox.isSelected
    b.repeatMacro = c.repeatMacroBox.isSelected; b.stepThrough = c.stepThroughBox.isSelected
    b.macroText = c.macroField.text
    b.keyChar = c.keyDropdown.selectedItem?.toString() ?: "A"
    b.shift = c.shiftBox.isSelected; b.ctrl = c.ctrlBox.isSelected
    b.alt = c.altBox.isSelected; b.win = c.winBox.isSelected
}

// --- TRAY & OTHER ---
fun setupSystemTray(frame: JFrame) {
    if (!SystemTray.isSupported()) { frame.isVisible = true; return }
    val tray = SystemTray.getSystemTray()

    var img: Image? = null
    try {
        val stream = PaddleBind::class.java.getResourceAsStream("/icon.png")
            ?: PaddleBind::class.java.classLoader.getResourceAsStream("icon.png")
        if (stream != null) {
            img = ImageIO.read(stream)
        }
    } catch (e: Exception) {}

    if (img == null) {
        val paths = arrayOf("icon.png", "app/src/main/resources/icon.png", "src/main/resources/icon.png")
        for (path in paths) {
            val f = File(path)
            if (f.exists()) {
                try {
                    img = ImageIO.read(f)
                    break
                } catch (e: Exception) {}
            }
        }
    }

    val trayImg: Image = if (img != null) {
        frame.iconImage = img
        img
    } else {
        val fallback = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        fallback.createGraphics().run {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color(0, 150, 255); fillOval(4, 4, 24, 24)
            color = Color.WHITE; font = Font("Arial", Font.BOLD, 12); drawString("SL", 8, 20); dispose()
        }
        fallback
    }

    val icon = TrayIcon(trayImg, "ShadowLink")
    icon.isImageAutoSize = true
    val menu = PopupMenu().apply {
        add(MenuItem("Open").apply { addActionListener { frame.isVisible = true; frame.state = Frame.NORMAL } })
        add(MenuItem("Exit").apply { addActionListener { System.exit(0) } })
    }
    icon.popupMenu = menu
    icon.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) { if (e.button == MouseEvent.BUTTON1) { frame.isVisible = true; frame.state = Frame.NORMAL } }
    })
    try { tray.add(icon) } catch (e: Exception) {}
    frame.addWindowStateListener { if (it.newState == Frame.ICONIFIED) frame.isVisible = false }
    frame.isVisible = !startMinimized
}

fun showMacroInstructions(parent: JFrame) {
    val helpText = """
        <html>
        <body style='width: 450px; font-family: sans-serif;'>
        <h2>Advanced Macro Creation Guide</h2>
        <p>Macros allow you to execute sequences of keystrokes, delays, and mouse movements. Separate each action with a comma.</p>
        
        <h3>Action Types:</h3>
        <ul>
            <li><b>Tap a Key:</b> Type the key name. <i>(e.g., <b>A</b> or <b>Enter</b>)</i></li>
            <li><b>Mouse Clicks:</b> Use <b>LClick</b>, <b>RClick</b>, or <b>MClick</b>.</li>
            <li><b>Hold a Key:</b> Type the key name followed by "down". <i>(e.g., <b>Ctrl down</b>)</i></li>
            <li><b>Release a Key:</b> Type the key name followed by "up". <i>(e.g., <b>Ctrl up</b>)</i></li>
            <li><b>Static Delay:</b> Type a number to wait in milliseconds. <i>(e.g., <b>500</b>)</i></li>
            <li><b>Random Delay:</b> Type a range separated by a tilde to humanize inputs! <i>(e.g., <b>50~150</b> waits a random amount of time between 50ms and 150ms)</i></li>
            <li><b>Mouse Moves:</b> Use absolute coordinates <i>(e.g., <b>MouseAbs 1920 1080</b>)</i> or relative moves <i>(e.g., <b>MouseDelta 0 50</b> moves the mouse 50 pixels down)</i>.</li>
        </ul>
        
        <h3>Step-Through Macros:</h3>
        <p>Checking <b>Step</b> changes how the macro fires. Every time you press the paddle, it will execute <b>only the next action</b> in your list, cycling back to the start when finished.</p>
        <br>
        <p style='color: #D32F2F;'><b>⚠️  Pro-Tip for Step Macros:</b> Do not use the Live Recorder for Step macros! The recorder captures individual "down", "delay", and "up" events, meaning you will have to press the paddle 3-4 times just to type one letter. For Step macros, manually type clean, comma-separated lists like: <code>A, B, C</code>.</p>
        </body>
        </html>
    """.trimIndent()

    val label = JLabel(helpText)
    JOptionPane.showMessageDialog(parent, label, "Macro Instructions", JOptionPane.INFORMATION_MESSAGE)
}

fun showLayerInstructions(parent: JFrame) {
    val helpText = """
        <html>
        <body style='width: 400px; font-family: sans-serif;'>
        <h2>Layer & Combo System Guide</h2>
        <p>Layers allow you to switch between entirely different sets of bindings on the fly. Combos allow you to trigger actions by pressing two buttons simultaneously!</p>
        
        <h3>Using Combo Button Macros:</h3>
        <ul>
            <li>Combo macros (like <b>M1 + M2</b>) trigger when both specific buttons are pressed.</li>
            <li>When you trigger a Combo Macro, the individual actions assigned to M1 and M2 are automatically canceled so they don't fire by mistake!</li>
            <li><b>Note:</b> You must ensure Combo Buttons are checked as "Enabled" in the UI for them to override single buttons.</li>
        </ul>
        
        <h3>Single Button vs. Combo Layer Toggles:</h3>
        <ul>
            <li><b>Single Toggle (e.g., Command):</b> If you assign only one button to switch Layers in Layer Settings, it becomes permanently reserved globally. You won't be able to assign standard inputs to it.</li>
            <li><b>Combo Toggle (e.g., M1 + M4):</b> If you assign a two-button combo for your Layer shift, you can still use those buttons individually! The system pauses your single actions only when you press them both together to shift layers.</li>
        </ul>
        </body>
        </html>
    """.trimIndent()

    val label = JLabel(helpText)
    JOptionPane.showMessageDialog(parent, label, "Layer & Combo Instructions", JOptionPane.INFORMATION_MESSAGE)
}

fun getKeyCode(key: String): Int? {
    return KEY_MAP[key] ?: KEY_MAP.entries.find { it.key.equals(key, ignoreCase = true) }?.value
}

fun getMouseMask(key: String): Int {
    return when (key.lowercase()) {
        "lclick" -> InputEvent.BUTTON1_DOWN_MASK
        "rclick" -> InputEvent.BUTTON2_DOWN_MASK
        "mclick" -> InputEvent.BUTTON3_DOWN_MASK
        else -> 0
    }
}

// --- NEW MOUSE/KEY INJECTOR LOGIC ---

fun pressKeyBind(robot: Robot, b: PaddleBind) {
    try {
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

fun releaseKeyBind(robot: Robot, b: PaddleBind) {
    try {
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

fun processMacroToken(robot: Robot, token: String, pressedKeys: MutableSet<Int>, pressedMouse: MutableSet<Int>) {
    val t = token.trim()
    if (t.isEmpty()) return

    // 1. Check for Randomized Delay (e.g., "50~150")
    if (t.contains("~")) {
        val parts = t.split("~")
        if (parts.size == 2) {
            val min = parts[0].trim().toLongOrNull()
            val max = parts[1].trim().toLongOrNull()
            if (min != null && max != null && min <= max) {
                Thread.sleep(Random.nextLong(min, max + 1))
                return
            }
        }
    }

    // 2. Check for Static Delay
    val d = t.toLongOrNull()
    if (d != null) {
        Thread.sleep(d)
        return
    }

    val p = t.split(Regex("\\s+"))

    // 3. Check for Mouse Movements
    if (p[0].equals("MouseAbs", ignoreCase = true) && p.size >= 3) {
        val x = p[1].toIntOrNull() ?: return
        val y = p[2].toIntOrNull() ?: return
        try { robot.mouseMove(x, y) } catch (e: Exception) {}
        return
    }

    if (p[0].equals("MouseDelta", ignoreCase = true) && p.size >= 3) {
        val dx = p[1].toIntOrNull() ?: return
        val dy = p[2].toIntOrNull() ?: return
        val currentPos = MouseInfo.getPointerInfo().location
        try { robot.mouseMove(currentPos.x + dx, currentPos.y + dy) } catch (e: Exception) {}
        return
    }

    // 4. Standard Keys and Mouse Clicks
    val tLower = t.lowercase()
    val act: String
    val keyStr: String

    // Smarter parsing to handle multi-word keys like "Page Up" or "NumPad 0" correctly
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
    val mouseMask = getMouseMask(key)

    if (mouseMask != 0) {
        try {
            when (act) {
                "down" -> { robot.mousePress(mouseMask); pressedMouse.add(mouseMask) }
                "up" -> { robot.mouseRelease(mouseMask); pressedMouse.remove(mouseMask) }
                else -> {
                    robot.mousePress(mouseMask); pressedMouse.add(mouseMask)
                    Thread.sleep(20)
                    robot.mouseRelease(mouseMask); pressedMouse.remove(mouseMask)
                }
            }
        } catch (e: Exception) {}
    } else {
        val k = getKeyCode(key) ?: return
        try {
            when (act) {
                "down" -> { robot.keyPress(k); pressedKeys.add(k) }
                "up" -> { robot.keyRelease(k); pressedKeys.remove(k) }
                else -> {
                    robot.keyPress(k); pressedKeys.add(k)
                    Thread.sleep(20)
                    robot.keyRelease(k); pressedKeys.remove(k)
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
                b.macroText.split(",").map { it.trim() }.forEach { token ->
                    processMacroToken(robot, token, pressedKeys, pressedMouse)
                }
            } while (b.repeatMacro && state.activeBind === b) // <--- Fixed to ensure repeats only check actual logical bind
        } catch (e: Exception) {
        } finally {
            pressedKeys.forEach { k -> try { robot.keyRelease(k) } catch (e: Exception) {} }
            pressedMouse.forEach { m -> try { robot.mouseRelease(m) } catch (e: Exception) {} }
        }
    }
    t.start()
    return t
}

fun executeMacroStep(robot: Robot, b: PaddleBind, state: ButtonState) {
    // Filter out delays (static numbers or random ranges)
    val tokens = b.macroText.split(",").map { it.trim() }.filter {
        it.isNotEmpty() && !it.contains("~") && it.toLongOrNull() == null
    }

    if (tokens.isEmpty()) return

    Thread {
        try {
            val token = tokens[state.stepIndex % tokens.size]
            val pressedKeys = mutableSetOf<Int>()
            val pressedMouse = mutableSetOf<Int>()

            processMacroToken(robot, token, pressedKeys, pressedMouse)

            state.stepIndex++
            if (state.stepIndex >= tokens.size) state.stepIndex = 0

        } catch (e: Exception) {}
    }.start()
}