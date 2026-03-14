package com.retholtz.shadowlink

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import org.hid4java.HidManager
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter

// --- DATA STRUCTURES ---

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

data class Profile(
    var name: String = "Default",
    var targetProcess: String = "", // e.g. "chrome.exe"
    val m1: PaddleBind = PaddleBind(keyChar = "J"),
    val m2: PaddleBind = PaddleBind(keyChar = "L"),
    val m3: PaddleBind = PaddleBind(keyChar = "G"),
    val m4: PaddleBind = PaddleBind(keyChar = "M")
)

// --- GLOBAL STATE ---

var profiles = mutableListOf<Profile>()
var activeProfile: Profile = Profile()
var autoSwitchEnabled = true
var startMinimized = false

val rootDir = File("profiles").apply { if (!exists()) mkdirs() }
val globalConfigFile = File("config.properties")

// --- KEY MAPPING ---

val SUPPORTED_KEYS = arrayOf(
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
    "`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "Backspace",
    "ESC", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
    "Insert", "Home", "Page Up", "Delete", "End", "Page Down",
    "Space", "Enter", "Tab", "Up", "Down", "Left", "Right",
    ",", ".", "/", "\\", ";", "'", "[", "]"
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
    "Up" to KeyEvent.VK_UP, "Down" to KeyEvent.VK_DOWN, "Left" to KeyEvent.VK_LEFT, "Right" to KeyEvent.VK_RIGHT,
    "-" to KeyEvent.VK_MINUS, "=" to KeyEvent.VK_EQUALS, "," to KeyEvent.VK_COMMA, "." to KeyEvent.VK_PERIOD,
    "/" to KeyEvent.VK_SLASH, "\\" to KeyEvent.VK_BACK_SLASH, ";" to KeyEvent.VK_SEMICOLON,
    "'" to KeyEvent.VK_QUOTE, "[" to KeyEvent.VK_OPEN_BRACKET, "]" to KeyEvent.VK_CLOSE_BRACKET,
    "`" to KeyEvent.VK_BACK_QUOTE, "Shift" to KeyEvent.VK_SHIFT, "Ctrl" to KeyEvent.VK_CONTROL, "Alt" to KeyEvent.VK_ALT, "Win" to KeyEvent.VK_WINDOWS
)

// --- MAIN ENTRY ---

fun main() {
    loadAllProfiles()

    SwingUtilities.invokeLater {
        createAndShowGUI()
    }

    Thread { runControllerSniffer() }.start()
    Thread { runAutoSwitchWatchdog() }.start()
}

// --- CORE UI ---

lateinit var profileCombo: JComboBox<String>
lateinit var processField: JTextField
lateinit var m1Controls: PaddleUIControls
lateinit var m2Controls: PaddleUIControls
lateinit var m3Controls: PaddleUIControls
lateinit var m4Controls: PaddleUIControls

fun createAndShowGUI() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

        val baseFont = Font("Segoe UI", Font.PLAIN, 15)
        UIManager.put("Label.font", baseFont)
        UIManager.put("CheckBox.font", baseFont)
        UIManager.put("ComboBox.font", baseFont)
        UIManager.put("Button.font", baseFont)
        UIManager.put("TextField.font", baseFont)
        UIManager.put("TitledBorder.font", baseFont.deriveFont(Font.BOLD))
    } catch (e: Exception) {}

    val frame = JFrame("ShadowLink - ROG Raikiri II")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(1200, 620)
    frame.setLocationRelativeTo(null)
    frame.layout = BorderLayout(10, 10)

    val topPanel = JPanel()
    topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)
    topPanel.border = BorderFactory.createTitledBorder("Profile Management")

    val profileRow = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
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

    profileRow.add(JLabel("Current Profile: "))
    profileRow.add(profileCombo)
    profileRow.add(newBtn)
    profileRow.add(deleteBtn)

    val switchRow = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
    processField = JTextField(activeProfile.targetProcess, 15)
    processField.preferredSize = Dimension(150, 30)

    val browseBtn = JButton("Browse...")
    browseBtn.addActionListener {
        val chooser = JFileChooser()
        val filter = FileNameExtensionFilter("Executables (*.exe)", "exe")
        chooser.fileFilter = filter
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            processField.text = chooser.selectedFile.name.lowercase()
        }
    }

    val activeAppsBtn = JButton("Active Apps...")
    activeAppsBtn.addActionListener {
        showActiveProcessDialog(frame)
    }

    val autoSwitchBox = JCheckBox("Auto-Switch Enabled", autoSwitchEnabled)
    autoSwitchBox.addActionListener { autoSwitchEnabled = autoSwitchBox.isSelected }

    switchRow.add(JLabel("Auto-Switch Executable: "))
    switchRow.add(processField)
    switchRow.add(browseBtn)
    switchRow.add(activeAppsBtn)
    switchRow.add(autoSwitchBox)

    topPanel.add(profileRow)
    topPanel.add(switchRow)
    frame.add(topPanel, BorderLayout.NORTH)

    val centerPanel = JPanel(GridLayout(4, 1, 5, 5))
    m1Controls = createPaddleRow("M1 (Bottom Left)", activeProfile.m1)
    m2Controls = createPaddleRow("M2 (Top Left)", activeProfile.m2)
    m3Controls = createPaddleRow("M3 (Top Right)", activeProfile.m3)
    m4Controls = createPaddleRow("M4 (Bottom Right)", activeProfile.m4)

    centerPanel.add(m1Controls.panel)
    centerPanel.add(m2Controls.panel)
    centerPanel.add(m3Controls.panel)
    centerPanel.add(m4Controls.panel)
    frame.add(centerPanel, BorderLayout.CENTER)

    val bottomPanel = JPanel(BorderLayout())
    val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 15, 10))
    val minimizedBox = JCheckBox("Start Minimized", startMinimized)
    minimizedBox.addActionListener { startMinimized = minimizedBox.isSelected }
    optionsPanel.add(minimizedBox)

    val helpBtn = JButton("Macro Help")
    helpBtn.addActionListener { showMacroInstructions(frame) }
    optionsPanel.add(helpBtn)

    bottomPanel.add(optionsPanel, BorderLayout.WEST)

    val saveBtn = JButton("Save & Apply All Settings")
    saveBtn.font = Font("Segoe UI", Font.BOLD, 16)
    saveBtn.addActionListener {
        updateActiveProfileFromUI()
        saveProfile(activeProfile)
        saveGlobalConfig()
        JOptionPane.showMessageDialog(frame, "Profile '${activeProfile.name}' Saved!")
    }
    bottomPanel.add(saveBtn, BorderLayout.EAST)
    frame.add(bottomPanel, BorderLayout.SOUTH)

    setupSystemTray(frame)
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

fun switchActiveProfile(name: String) {
    val found = profiles.find { it.name == name }
    if (found != null) {
        activeProfile = found
        println("Switched to profile: ${found.name}")
    }
}

fun refreshUI() {
    processField.text = activeProfile.targetProcess
    profileCombo.selectedItem = activeProfile.name
    refreshPaddleRow(m1Controls, activeProfile.m1)
    refreshPaddleRow(m2Controls, activeProfile.m2)
    refreshPaddleRow(m3Controls, activeProfile.m3)
    refreshPaddleRow(m4Controls, activeProfile.m4)
}

fun updateActiveProfileFromUI() {
    activeProfile.targetProcess = processField.text.trim().lowercase()
    updateBindFromUI(activeProfile.m1, m1Controls)
    updateBindFromUI(activeProfile.m2, m2Controls)
    updateBindFromUI(activeProfile.m3, m3Controls)
    updateBindFromUI(activeProfile.m4, m4Controls)
}

/**
 * Monitors the active window and switches profiles automatically.
 */
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
        Thread.sleep(1500) // Check every 1.5 seconds
    }
}

/**
 * Windows JNA call to get the executable name of the focused window.
 */
fun getForegroundProcessName(): String {
    val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return ""
    return getProcessNameFromHwnd(hwnd)
}

// --- SNIFFER (Modified to use activeProfile) ---

fun runControllerSniffer() {
    val hidServices = HidManager.getHidServices()
    val robot = Robot().apply { isAutoWaitForIdle = false }

    while (true) {
        val raikiri = hidServices.attachedHidDevices
            .filter { it.vendorId == 0x0B05 }
            .find { Integer.toHexString(it.usagePage).endsWith("c3") }

        if (raikiri != null && raikiri.open()) {
            var m1Pressed = false; var m2Pressed = false; var m3Pressed = false; var m4Pressed = false
            var isConnected = true

            while (isConnected) {
                val data = ByteArray(64)
                val read = raikiri.read(data, 500)
                if (read > 0 && (data[0].toInt() and 0xFF) == 0xB3) {
                    val p = activeProfile

                    val s1 = data[8].toInt() == 1; val s2 = data[6].toInt() == 1
                    val s3 = data[5].toInt() == 1; val s4 = data[7].toInt() == 1

                    fun handle(state: Boolean, last: Boolean, bind: PaddleBind): Boolean {
                        if (bind.enabled) {
                            if (state && !last) { if (bind.isMacro) executeMacro(robot, bind.macroText) else pressKeyBind(robot, bind) }
                            else if (!state && last) { if (!bind.isMacro) releaseKeyBind(robot, bind) }
                        }
                        return state
                    }

                    m1Pressed = handle(s1, m1Pressed, p.m1)
                    m2Pressed = handle(s2, m2Pressed, p.m2)
                    m3Pressed = handle(s3, m3Pressed, p.m3)
                    m4Pressed = handle(s4, m4Pressed, p.m4)
                } else if (read < 0) {
                    raikiri.close(); isConnected = false
                }
            }
        } else {
            Thread.sleep(2000)
        }
    }
}

// --- PERSISTENCE ---

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

            fun loadB(prefix: String, b: PaddleBind) {
                b.enabled = props.getProperty("${prefix}_EN", "true").toBoolean()
                b.isMacro = props.getProperty("${prefix}_MAC", "false").toBoolean()
                b.macroText = props.getProperty("${prefix}_TXT", "")
                b.keyChar = props.getProperty("${prefix}_KEY", "A")
                b.shift = props.getProperty("${prefix}_SH", "false").toBoolean()
                b.ctrl = props.getProperty("${prefix}_CT", "false").toBoolean()
                b.alt = props.getProperty("${prefix}_AL", "false").toBoolean()
                b.win = props.getProperty("${prefix}_WI", "false").toBoolean()
            }
            loadB("M1", p.m1); loadB("M2", p.m2); loadB("M3", p.m3); loadB("M4", p.m4)
            profiles.add(p)
        }

        if (globalConfigFile.exists()) {
            val global = Properties()
            FileInputStream(globalConfigFile).use { global.load(it) }
            val lastActive = global.getProperty("ACTIVE_PROFILE", "Default")
            autoSwitchEnabled = global.getProperty("AUTO_SWITCH", "true").toBoolean()
            startMinimized = global.getProperty("START_MINIMIZED", "false").toBoolean()
            activeProfile = profiles.find { it.name == lastActive } ?: profiles[0]
        } else {
            activeProfile = profiles[0]
        }
    }
}

fun saveProfile(p: Profile) {
    val props = Properties()
    props.setProperty("TARGET_PROCESS", p.targetProcess)
    fun saveB(prefix: String, b: PaddleBind) {
        props.setProperty("${prefix}_EN", b.enabled.toString())
        props.setProperty("${prefix}_MAC", b.isMacro.toString())
        props.setProperty("${prefix}_TXT", b.macroText)
        props.setProperty("${prefix}_KEY", b.keyChar)
        props.setProperty("${prefix}_SH", b.shift.toString())
        props.setProperty("${prefix}_CT", b.ctrl.toString())
        props.setProperty("${prefix}_AL", b.alt.toString())
        props.setProperty("${prefix}_WI", b.win.toString())
    }
    saveB("M1", p.m1); saveB("M2", p.m2); saveB("M3", p.m3); saveB("M4", p.m4)
    FileOutputStream(File(rootDir, "${p.name}.properties")).use { props.store(it, null) }
}

fun saveGlobalConfig() {
    val props = Properties()
    props.setProperty("ACTIVE_PROFILE", activeProfile.name)
    props.setProperty("AUTO_SWITCH", autoSwitchEnabled.toString())
    props.setProperty("START_MINIMIZED", startMinimized.toString())
    FileOutputStream(globalConfigFile).use { props.store(it, null) }
}

// --- UI HELPERS ---

class PaddleUIControls(
    val panel: JPanel, val enabledBox: JCheckBox, val isMacroBox: JCheckBox,
    val macroField: JTextField, val shiftBox: JCheckBox, val ctrlBox: JCheckBox,
    val altBox: JCheckBox, val winBox: JCheckBox, val keyDropdown: JComboBox<String>
)

fun createPaddleRow(name: String, bind: PaddleBind): PaddleUIControls {
    val panel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 10))
    panel.add(JLabel("$name: "))
    val en = JCheckBox("Enabled", bind.enabled)
    val mac = JCheckBox("Macro", bind.isMacro)
    val txt = JTextField(bind.macroText, 15)

    val sh = JCheckBox("Shift", bind.shift)
    val ct = JCheckBox("Ctrl", bind.ctrl)
    val al = JCheckBox("Alt", bind.alt)
    val wi = JCheckBox("Win", bind.win)

    val key = JComboBox(SUPPORTED_KEYS).apply { selectedItem = bind.keyChar }

    fun vis() {
        val e = en.isSelected; val m = mac.isSelected
        mac.isEnabled = e; txt.isEnabled = e && m; key.isEnabled = e && !m
        sh.isEnabled = e && !m; ct.isEnabled = e && !m; al.isEnabled = e && !m; wi.isEnabled = e && !m
        txt.isVisible = m; key.isVisible = !m; sh.isVisible = !m; ct.isVisible = !m; al.isVisible = !m; wi.isVisible = !m
        panel.revalidate()
    }
    en.addActionListener { vis() }; mac.addActionListener { vis() }

    panel.add(en); panel.add(mac); panel.add(sh); panel.add(ct); panel.add(al); panel.add(wi); panel.add(key); panel.add(txt)
    vis()
    return PaddleUIControls(panel, en, mac, txt, sh, ct, al, wi, key)
}

fun refreshPaddleRow(c: PaddleUIControls, b: PaddleBind) {
    c.enabledBox.isSelected = b.enabled
    c.isMacroBox.isSelected = b.isMacro
    c.macroField.text = b.macroText
    c.shiftBox.isSelected = b.shift
    c.ctrlBox.isSelected = b.ctrl
    c.altBox.isSelected = b.alt
    c.winBox.isSelected = b.win
    c.keyDropdown.selectedItem = b.keyChar
    c.enabledBox.actionListeners.forEach { it.actionPerformed(null) }
}

fun updateBindFromUI(b: PaddleBind, c: PaddleUIControls) {
    b.enabled = c.enabledBox.isSelected; b.isMacro = c.isMacroBox.isSelected
    b.macroText = c.macroField.text; b.keyChar = c.keyDropdown.selectedItem?.toString() ?: "A"
    b.shift = c.shiftBox.isSelected; b.ctrl = c.ctrlBox.isSelected
    b.alt = c.altBox.isSelected; b.win = c.winBox.isSelected
}

// --- TRAY & OTHER ---

fun setupSystemTray(frame: JFrame) {
    if (!SystemTray.isSupported()) { frame.isVisible = true; return }
    val tray = SystemTray.getSystemTray()

    // RELIABLE ICON LOADING:
    // 1. Try classpath resource relative to OUR code (the "Kt" class)
    // 2. Try file system paths for IDE runs
    var img: Image? = null
    try {
        val stream = PaddleBind::class.java.getResourceAsStream("/icon.png")
            ?: PaddleBind::class.java.classLoader.getResourceAsStream("icon.png")
        if (stream != null) {
            img = ImageIO.read(stream)
        }
    } catch (e: Exception) {}

    if (img == null) {
        // Fallback for IDE: check absolute and relative locations
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
        // Fallback: Draw the blue circle if file is absolutely missing
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
    val msg = "Macros: comma separated keys or delays.\n" +
            "Example: Ctrl down, S, 50, Ctrl up\n" +
            "Syntax: [Key] [down|up] or [delayInMs]"
    JOptionPane.showMessageDialog(parent, msg)
}

fun getKeyCode(key: String) = KEY_MAP[key]

fun pressKeyBind(robot: Robot, b: PaddleBind) {
    val k = getKeyCode(b.keyChar) ?: return
    try {
        if (b.win) robot.keyPress(KeyEvent.VK_WINDOWS)
        if (b.shift) robot.keyPress(KeyEvent.VK_SHIFT)
        if (b.ctrl) robot.keyPress(KeyEvent.VK_CONTROL)
        if (b.alt) robot.keyPress(KeyEvent.VK_ALT)
        robot.keyPress(k)
    } catch (e: Exception) {}
}

fun releaseKeyBind(robot: Robot, b: PaddleBind) {
    val k = getKeyCode(b.keyChar) ?: return
    try {
        robot.keyRelease(k)
        if (b.alt) robot.keyRelease(KeyEvent.VK_ALT)
        if (b.ctrl) robot.keyRelease(KeyEvent.VK_CONTROL)
        if (b.shift) robot.keyRelease(KeyEvent.VK_SHIFT)
        if (b.win) robot.keyRelease(KeyEvent.VK_WINDOWS)
    } catch (e: Exception) {}
}

fun executeMacro(robot: Robot, text: String) {
    Thread {
        text.split(",").map { it.trim() }.forEach { t ->
            if (t.isNotEmpty()) {
                val d = t.toLongOrNull()
                if (d != null) Thread.sleep(d)
                else {
                    val p = t.split(" ")
                    val key = p[0]; val act = if (p.size > 1) p[1].lowercase() else "tap"
                    val k = getKeyCode(key) ?: return@forEach
                    when (act) {
                        "down" -> robot.keyPress(k)
                        "up" -> robot.keyRelease(k)
                        else -> { robot.keyPress(k); Thread.sleep(20); robot.keyRelease(k) }
                    }
                }
            }
        }
    }.start()
}