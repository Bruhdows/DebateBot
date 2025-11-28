plugins {
    id("java")
    id("io.freefair.lombok") version "9.1.0"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.bruhdows"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:6.1.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("ch.qos.logback:logback-classic:1.5.21")
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.yaml:snakeyaml:2.2")
}

tasks {
    shadowJar {
        manifest {
            attributes["Main-Class"] = "com.bruhdows.bruhbot.BruhBot"
        }
    }

    jar {
        dependsOn(shadowJar)
    }
}