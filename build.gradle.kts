plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "fr.noltox.hcplugins"
version = providers.gradleProperty("version").get()

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

dependencies {
    implementation("xyz.xenondevs.invui:invui:2.3.2")
    implementation("de.exlll:configlib-paper:4.8.1")
    compileOnly("fr.noltox.hcplugins:core-api")
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("net.william278.huskhomes:huskhomes-bukkit:4.11")
    compileOnly("me.clip:placeholderapi:2.12.3")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("paper-plugin.yml") { expand("version" to pluginVersion) }
}

tasks.jar { enabled = false }
tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("HCHuskHomesGUI-${project.version}.jar")
    minimize()
    failOnDuplicateEntries = true
    filesMatching("META-INF/*.kotlin_module") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    relocate("xyz.xenondevs.invui", "fr.noltox.hcplugins.huskhomesgui.libs.invui")
    relocate("de.exlll.configlib", "fr.noltox.hcplugins.huskhomesgui.libs.configlib")
    relocate("fr.noltox.hcconfig", "fr.noltox.hcplugins.huskhomesgui.libs.hcconfig")
}
tasks.build { dependsOn(tasks.shadowJar) }
tasks.test { useJUnitPlatform() }
