plugins {
    id("synthient.library-conventions")
}

description = "Fetches the protobuf schema of the Synthient gRPC API."

dependencies {
    api(project(":"))
    api(libs.grpc.api)
    api(libs.protobuf.java)
    implementation(libs.grpc.services)
    implementation(libs.grpc.stub)
    runtimeOnly(libs.grpc.netty.shaded)
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "synthient-java-grpcschema"
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "com.synthient.grpcschema")
    }
}
