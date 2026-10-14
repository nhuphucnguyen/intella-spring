import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.phucngu.intellaspring"
version = "0.4.0"

java {
    // The 2026.2 platform is built with Java 25; plugin compilation must match.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // IDE-level tests (BasePlatformTestCase) are JUnit 3/4 style; the vintage engine runs them on the JUnit Platform.
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

    intellijPlatform {
        // Single unified distribution (2025.3+); the Spring plugins shipped in it are
        // Ultimate-license-gated, which is exactly the gap this plugin fills.
        intellijIdea("2026.2")
        // Java PSI (annotations, DOM, JAM, UAST, execution) for the bean model and run configs.
        bundledPlugin("com.intellij.java")
        // Properties + YAML PSI for application.properties / application.yml support.
        bundledPlugin("com.intellij.properties")
        bundledPlugin("org.jetbrains.plugins.yaml")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
    }
}
