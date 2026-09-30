# synthient-java

![Synthient](assets/readme-header.svg)

[![Maven Central](https://img.shields.io/maven-central/v/com.synthient/synthient-java)](https://central.sonatype.com/artifact/com.synthient/synthient-java)
[![test](https://github.com/synthient/synthient-java/actions/workflows/test.yml/badge.svg)](https://github.com/synthient/synthient-java/actions/workflows/test.yml)
[![lint](https://github.com/synthient/synthient-java/actions/workflows/lint.yml/badge.svg)](https://github.com/synthient/synthient-java/actions/workflows/lint.yml)

The official Java client for the [Synthient API](https://docs.synthient.com): IP and domain intelligence, account details, and real-time and bulk data feeds.

## Install

Gradle:

```kotlin
implementation("com.synthient:synthient-java:1.0.0")
```

Maven:

```xml
<dependency>
  <groupId>com.synthient</groupId>
  <artifactId>synthient-java</artifactId>
  <version>1.0.0</version>
</dependency>
```

Requires Java 21 or later.

## Usage

```java
SynthientClient client = new SynthientClient(); // reads SYNTHIENT_API_KEY

IpInfo info = client.lookupIp("8.8.8.8");
if (info.intelligence() != null) {
    System.out.println(info.intelligence().riskScore() + " " + info.intelligence().categories());
}
```

Look up many addresses in one request:

```java
List<IpInfo> results = client.lookupIps(List.of("8.8.8.8", "1.1.1.1"));
for (IpInfo result : results) {
    if (result.intelligence() != null) {
        System.out.println(result.ip() + " " + result.intelligence().riskScore());
    }
}
```

Consume a real-time feed:

```java
try (EventStream<ProxyEvent> events = client.proxies().stream()) {
    for (ProxyEvent event : events) {
        System.out.println(event.ip() + " " + event.provider());
    }
}
```

## Documentation

Every public class and method is documented in its [Javadoc](https://javadoc.io/doc/com.synthient/synthient-java), including errors, retries, exports, and downloads. Runnable programs are in [examples](examples/src/main/java/com/synthient/examples), for example `./gradlew -q :examples:lookupIp --args=8.8.8.8`. Guides and field references are at [docs.synthient.com](https://docs.synthient.com). The `com.synthient:synthient-java-grpcschema` artifact fetches the protobuf schema of the Synthient gRPC API.

## License

[Apache 2.0](LICENSE)
