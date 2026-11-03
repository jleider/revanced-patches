dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:baconreader:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
}

android {
    defaultConfig {
        minSdk = 22
    }
}
