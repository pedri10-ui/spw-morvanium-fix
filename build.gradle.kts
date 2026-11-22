plugins {
    id("java-library")
    kotlin("jvm") version "2.4.20"
    id("com.xuncorp.spw.workshop") version "0.1.0-dev22"
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.spw.workshop.api)
}

spmod {
    PluginClass = "com.fix.morvanium.MainPlugin"
    PluginId = "com.fix.morvanium.sync"
    PluginVersion = "1.0.0"
    PluginProvider = "CustomFix"
    PluginName = "Morvanium Time & Discord RPC Fix"
    PluginDescription = "Corrige o travamento de segundos e duração no Discord Rich Presence durante transições do Morvanium."
}
