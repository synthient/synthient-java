plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "consumer-test"

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "staging"
                    url = uri("../build/staging-repo")
                }
            }
            filter {
                includeGroup("com.synthient")
            }
        }
        mavenCentral()
    }
}
