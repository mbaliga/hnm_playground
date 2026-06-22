plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("dev.hnm.workbench.desktop.MainKt")
}
