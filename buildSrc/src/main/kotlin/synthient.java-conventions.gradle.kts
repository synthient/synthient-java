import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = versionCatalogs.named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    errorprone(libs.findLibrary("errorprone-core").get())
    errorprone(libs.findLibrary("nullaway").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.check("NullAway", CheckSeverity.OFF)
}

spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        formatAnnotations()
    }
}
