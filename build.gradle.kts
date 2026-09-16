import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.3.0"
    id("de.eldoria.plugin-yml.bukkit") version "0.8.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "net.guizhanss"
description = "InfinityExpansion2 compatibility fork"

val mainPackage = "net.guizhanss.infinityexpansion2"
val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("1.21.11-R0.1-SNAPSHOT")
val slimefunApiCoordinate = providers.gradleProperty("slimefunApiCoordinate").orElse("com.github.slimefun:Slimefun4:experimental-SNAPSHOT")
val targetJvm = providers.gradleProperty("targetJvm").orElse("21").get().toInt()
version = providers.gradleProperty("buildVersion").orElse("2.0.7").get()

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    maven("https://repo.alessiodp.com/releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly(slimefunApiCoordinate.get())
    compileOnly("net.guizhanss:SlimefunTranslation:e03b01a7b7")
    compileOnly("com.github.schntgaispock:SlimeHUD:1.3.0")
    implementation("net.guizhanss:guizhanlib-all:2.5.0")
    implementation("net.guizhanss:guizhanlib-kt-all:0.2.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("com.jeff-media:MorePersistentDataTypes:2.4.0")
}

java {
    disableAutoTargetJvm()
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.toVersion(targetJvm)
    targetCompatibility = JavaVersion.toVersion(targetJvm)
}

tasks.withType<JavaCompile>().configureEach { options.release.set(targetJvm) }

kotlin {
    jvmToolchain(25)
    compilerOptions {
        javaParameters = true
        jvmTarget = JvmTarget.fromTarget(targetJvm.toString())
    }
}

tasks.shadowJar {
    fun doRelocate(from: String, to: String? = null) {
        val last = to ?: from.split(".").last()
        relocate(from, "$mainPackage.libs.$last")
    }
    doRelocate("net.byteflux.libby")
    doRelocate("net.guizhanss.guizhanlib")
    doRelocate("org.bstats")
    doRelocate("io.github.seggan.sf4k")
    doRelocate("io.papermc.lib", "paperlib")
    doRelocate("com.jeff_media.morepersistentdatatypes")
    minimize()
    archiveClassifier = ""
    // Historical public basename: version 2.x follows InfinityExpansion directly.
    archiveFileName.set("SF_InfinityExpansion${project.version}.jar")
}

bukkit {
    main = "$mainPackage.InfinityExpansion2"
    apiVersion = "1.21"
    authors = listOf("ybw0014", "Mooy1", "wickidcow")
    description = "InfinityExpansion2 - Slimefun compatibility and IE1 migration fork"
    depend = listOf("Slimefun")
    softDepend = listOf("GuizhanLibPlugin", "SlimefunTranslation", "InfinityExpansion", "SlimeHUD")
    loadBefore = listOf("SlimeCustomizer", "RykenSlimeCustomizer", "SlimeFunRecipe")
    commands {
        register("infinityexpansion2") {
            description = "InfinityExpansion2 command"
            aliases = listOf("ie", "ie2")
        }
    }
    permissions {
        register("infinityexpansion2.command.doctor") { default = BukkitPluginDescription.Permission.Default.OP }
        register("infinityexpansion2.command.giverecipe") { default = BukkitPluginDescription.Permission.Default.OP }
        register("infinityexpansion2.command.guide") { default = BukkitPluginDescription.Permission.Default.TRUE }
        register("infinityexpansion2.command.printitem") { default = BukkitPluginDescription.Permission.Default.OP }
        register("infinityexpansion2.command.id") { default = BukkitPluginDescription.Permission.Default.OP }
    }
}

tasks {
    runServer {
        jvmArgs("-Dcom.mojang.eula.agree=true")
        minecraftVersion("26.2")
    }
}
