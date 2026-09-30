plugins {
    id("synthient.library-conventions")
}

description = "The official Java client for the Synthient API."

dependencies {
    api(libs.jackson.annotations)
    api(libs.jspecify)
    api(libs.okhttp)
    implementation(libs.jackson.databind)
}

tasks.test {
    systemProperty("synthient.version", version.toString())
    systemProperty("synthient.spec", file(".sdkgen/openapi.json").absolutePath)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "com.synthient")
    }
}

// OkHttp is a Kotlin Multiplatform library whose `okhttp` artifact is empty for Maven, so the POM names the
// JVM artifact. Gradle consumers keep `okhttp` through the module metadata, where version conflicts resolve.
publishing.publications.named<MavenPublication>("maven") {
    pom.withXml {
        val artifactIds = asElement().getElementsByTagName("artifactId")
        for (i in 0 until artifactIds.length) {
            val artifactId = artifactIds.item(i)
            if (artifactId.textContent == "okhttp" && artifactId.parentNode.nodeName == "dependency") {
                artifactId.textContent = "okhttp-jvm"
            }
        }
    }
}
