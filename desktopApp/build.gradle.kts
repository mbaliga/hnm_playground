plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":backend-desktop"))
}

application {
    mainClass.set("dev.hnm.workbench.desktop.MainKt")
}
