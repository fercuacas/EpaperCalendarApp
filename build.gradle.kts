buildscript {
    repositories {
        maven {
            url = uri("https://maven.google.com")
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
    }
}
