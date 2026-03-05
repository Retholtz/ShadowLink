ShadowLink 🎮

A lightweight, standalone background service for remapping the ASUS ROG Raikiri II back paddles.

ShadowLink allows you to bind any keyboard key (along with Shift, Ctrl, and Alt modifiers) directly to the physical M1, M2, M3, and M4 back paddles of your ROG Raikiri II controller. It runs completely standalone—no Armoury Crate required!

📥 Download

Because the bundled Java Runtime Environment (JRE) makes the release file too large for standard GitHub hosting, the official compiled release is hosted safely on Google Drive:

👉 [**Download ShadowLink v1.0 (.ZIP)**](https://drive.google.com/file/d/1qeFFT9yIkYiEo-6vixSCb2_asraTmT9O/view?usp=drive_link)

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
