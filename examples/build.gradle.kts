plugins {
    id("synthient.java-conventions")
}

dependencies {
    implementation(project(":"))
    implementation(project(":grpcschema"))
}

val examples = listOf(
    "AccountExample",
    "DownloadExportExample",
    "GrpcSchemaExample",
    "LookupDomainExample",
    "LookupIpExample",
    "LookupIpsExample",
    "StreamExample",
)

for (example in examples) {
    tasks.register<JavaExec>(example.removeSuffix("Example").replaceFirstChar(Char::lowercase)) {
        group = "examples"
        description = "Runs com.synthient.examples.$example."
        mainClass = "com.synthient.examples.$example"
        classpath = sourceSets.main.get().runtimeClasspath
    }
}
