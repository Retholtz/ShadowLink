plugins {
    // We remove the version number here to fix the "already on classpath" error.
    // Android Studio handles the Kotlin version at the project level.
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"

    // NEW: The Launch4j plugin to generate the .exe file
    id("edu.sc.seis.launch4j") version "3.0.7"
}

group = "com.retholtz"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // HID libraries for Raikiri II communication
    implementation("org.hid4java:hid4java:0.7.0")
    implementation("net.java.dev.jna:jna:5.13.0")
}

// Configures the shadowJar task to package everything into a single runnable file.
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    // FIX: Remove the "-all" suffix so the fat jar replaces the default jar.
    // This completely bypasses the Kotlin DSL name collision for Launch4j!
    archiveClassifier.set("")

    manifest {
        attributes["Main-Class"] = "com.retholtz.shadowlink.MainKt"
    }
}

// --- NEW: Launch4j Executable Configuration ---
launch4j {
    mainClassName.set("com.retholtz.shadowlink.MainKt")

    // "gui" makes it a standard Windows app and hides the black command prompt box!
    headerType.set("gui")

    outfile.set("ShadowLink.exe")

    // --- ICON CONFIGURATION ---
    // Tells Launch4j to inject this .ico file into the final executable.
    // We use ${projectDir} to force Gradle to look inside the "app" folder!
    icon.set("${projectDir}/icon.ico")

    // We removed the conflicting jar configuration line completely.
    // Because we set archiveClassifier.set("") above, Launch4j will automatically
    // find and use the correct ShadowJar file by default!

    // --- JRE BUNDLING CONFIGURATION ---
    // Tells the .exe to look for a folder named "jre" in the exact same directory
    bundledJrePath.set("jre")
    jreMinVersion.set("21")

    windowTitle.set("ShadowLink")
    errTitle.set("ShadowLink Error")
}

// Ensure the executable builder waits for the shadow jar to finish compiling first
tasks.named("createExe") {
    dependsOn("shadowJar")
}