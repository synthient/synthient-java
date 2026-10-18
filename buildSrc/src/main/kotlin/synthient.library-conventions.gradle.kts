plugins {
    id("synthient.java-conventions")
    `java-library`
    `maven-publish`
    signing
}

val libs = versionCatalogs.named("libs")

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.findVersion("junit").get().requiredVersion)
        }
    }
}

tasks.withType<Test>().configureEach {
    val testJava = providers.gradleProperty("testJava").orElse("21").map(String::toInt)
    javaLauncher = javaToolchains.launcherFor { languageVersion = testJava.map(JavaLanguageVersion::of) }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("-release", "21")
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Werror", true)
        addBooleanOption("quiet", true)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = provider { artifactId }
                description = provider { project.description }
                url = "https://github.com/synthient/synthient-java"
                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        name = "Synthient"
                        url = "https://synthient.com"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/synthient/synthient-java.git"
                    developerConnection = "scm:git:ssh://git@github.com/synthient/synthient-java.git"
                    url = "https://github.com/synthient/synthient-java"
                }
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-repo"))
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY")
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, System.getenv("SIGNING_PASSWORD"))
        sign(publishing.publications)
    }
}
