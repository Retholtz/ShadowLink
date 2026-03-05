ShadowLink 🎮

A lightweight, standalone background service for remapping the ASUS ROG Raikiri II back paddles.

ShadowLink allows you to bind any keyboard key (along with Shift, Ctrl, and Alt modifiers) directly to the physical M1, M2, M3, and M4 back paddles of your ROG Raikiri II controller. The proprietary ASUS driver (ASCEHIDRemp.sys) is required for Windows to identify the Raikiri back buttons, but ShadowLink is designed to run entirely without the Armoury Crate background services.

📥 Download

Because the bundled Java Runtime Environment (JRE) makes the release file too large for standard GitHub hosting, the official compiled release is hosted safely on Google Drive:

👉 [**Download ShadowLink v1.0 (.ZIP)**](https://drive.google.com/drive/folders/1VV3Ama4ZjavI17nwl7Z_RAv336hR1z-L?usp=sharing)


🚀 How to UseExtract the ZIP file:
1. Unzip the downloaded folder anywhere on your PC (e.g., your Desktop).
2. Run the App: Inside the extracted folder, double-click ShadowLink.exe.
     * Note: Keep the jre folder right next to the .exe. The app uses it to run natively without requiring you to install Java on your PC!
3. Configure your bindings:
     * M1: Bottom Left Paddle
     * M2: Top Left Paddle
     * M3: Top Right Paddle
     * M4: Bottom Right Paddle
4. Save & Play: Click "Save & Apply". You can safely leave the window open or minimize it. The background service automatically detects when your controller is plugged in (or swapped to the 2.4GHz dongle) and instantly applies your bindings.

⚙️ Driver Setup & Troubleshooting (Running Without Armoury Crate)

ShadowLink needs the official ASUS USB filter driver (ASCEHIDRemp.sys) to see the raw paddle inputs. Standard Windows generic Xbox drivers will hide these buttons. If your paddles are not being detected, choose one of the methods below to get the driver.

Method 1: The "Install and Disable" Method (Recommended)
This is the easiest way to get the correct driver while keeping your system resources free.

1. Download and install the official ASUS Armoury Crate software.

2. Plug in your Raikiri II and let Armoury Crate recognize it (this installs the necessary drivers).

3. Open the Windows Services app (press Win+R, type services.msc, and hit Enter).

4. Locate any services starting with "Armoury Crate" or "ASUS" (e.g., Armoury Crate Control Interface). Right-click them, select Properties, and change their Startup Type to Disabled.

5. Restart your PC. The drivers will remain active, but the heavy ASUS software will no longer run or conflict with ShadowLink.

Method 2: The Clean Extraction Method (For Advanced Users)
If you have another PC (like a laptop) that already has Armoury Crate installed and you want to keep your desktop completely clean:

1. On the PC with Armoury Crate, open Command Prompt as Administrator.

2. Run this command to extract all third-party drivers to a folder on your C: drive: pnputil /export-driver * C:\DriverBackup

3. Open C:\DriverBackup and search for ASCEHIDRemp.sys. Copy the specific folder containing that file to a USB flash drive.

4. Plug the Raikiri II into your clean PC.

5. Open Device Manager, find the controller under "Xbox Peripherals", right-click it, and select Update driver.

6. Choose Browse my computer for drivers, point it to the folder on your USB drive, and let Windows install the standalone ASUS driver.

    *** Note: Windows may not install this driver as it considers the Xbox driver to be the most current. I personally had to extract the drivers and run the acsehidremap.inf file to manually install the drivers ***

🛠️ Building from Source (For Developers)

If you want to modify the code or compile the .exe yourself, ShadowLink uses Gradle and Launch4j.
1. Clone this repository to your local machine.
2. Open a terminal (PowerShell/Command Prompt) in the project's root folder.
3. Run the following command to bundle the code and generate the executable:.\gradlew createExe
4. The final ShadowLink.exe will be generated in app/build/launch4j/.

🙏 Acknowledgements:

Adoptium Eclipse Temurin: The compiled Windows release bundles the open-source Eclipse Temurin Java Runtime Environment (JRE 21) so end-users do not need to install Java manually.

hid4java: Used for fast, reliable, low-level USB/Bluetooth HID sniffing to read the Raikiri II's paddle data states.

☕ Support the Project

If ShadowLink helped you dominate in your favorite game, consider buying me a coffee!

<a href="https://www.buymeacoffee.com/Retholtz" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

📄 License

This project is open-source and available under the MIT License. Feel free to use, modify, and distribute!
