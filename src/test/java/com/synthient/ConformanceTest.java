package com.synthient;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.synthient.model.Account;
import com.synthient.model.AccountApiKey;
import com.synthient.model.ActivityStats;
import com.synthient.model.AnonymizerEvent;
import com.synthient.model.AsnCount;
import com.synthient.model.Cadence;
import com.synthient.model.CountryCount;
import com.synthient.model.Device;
import com.synthient.model.DomainEvent;
import com.synthient.model.DomainInfo;
import com.synthient.model.DomainStats;
import com.synthient.model.DomainStatus;
import com.synthient.model.Export;
import com.synthient.model.ExportKind;
import com.synthient.model.ExportMeta;
import com.synthient.model.ExportPage;
import com.synthient.model.HeliosAdbEvent;
import com.synthient.model.HeliosDnsEvent;
import com.synthient.model.HeliosHttpEvent;
import com.synthient.model.HeliosHttpsEvent;
import com.synthient.model.HeliosMeta;
import com.synthient.model.Intelligence;
import com.synthient.model.IpInfo;
import com.synthient.model.Ja4tEvent;
import com.synthient.model.Location;
import com.synthient.model.LookupQuota;
import com.synthient.model.Network;
import com.synthient.model.NetworkType;
import com.synthient.model.OperatingSystem;
import com.synthient.model.Organization;
import com.synthient.model.ParquetColumn;
import com.synthient.model.ParquetSchema;
import com.synthient.model.ParquetType;
import com.synthient.model.PortCount;
import com.synthient.model.Provider;
import com.synthient.model.ProxyEvent;
import com.synthient.model.ProxyType;
import com.synthient.model.Relation;
import com.synthient.model.Scope;
import com.synthient.model.SubdomainCount;
import com.synthient.model.TimeSeriesPoint;
import com.synthient.model.TlsClientHello;
import com.synthient.model.TlsCodeName;
import com.synthient.model.TlsExtension;
import com.synthient.model.TorrentEvent;
import com.synthient.model.TorrentFile;
import com.synthient.model.TorrentPeer;
import com.synthient.model.UniqueIps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

/** Checks the SDK against the OpenAPI spec it was built from, {@code .sdkgen/openapi.json}. */
class ConformanceTest {
    private static final JsonNode SPEC = readSpec();

    private static final Map<String, Class<?>> MODELS = Map.ofEntries(
            entry("AccountInfoResponse", Account.class),
            entry("AccountAPIKey", AccountApiKey.class),
            entry("Organization", Organization.class),
            entry("LookupQuota", LookupQuota.class),
            entry("IpProvidersResponse", IpInfo.class),
            entry("NetworkInfo", Network.class),
            entry("LocationInfo", Location.class),
            entry("IntelligenceInfo", Intelligence.class),
            entry("DeviceInfo", Device.class),
            entry("ProviderEntry", Provider.class),
            entry("DomainLookupResponse", DomainInfo.class),
            entry("Stats", DomainStats.class),
            entry("TimeSeriesEntry", TimeSeriesPoint.class),
            entry("UniqueIPsBlock", UniqueIps.class),
            entry("SubdomainEntry", SubdomainCount.class),
            entry("PortEntry", PortCount.class),
            entry("CountryEntry", CountryCount.class),
            entry("ASNEntry", AsnCount.class),
            entry("ActivityStatsBlock", ActivityStats.class),
            entry("RecentEvent", DomainEvent.class),
            entry("ExportListResponse", ExportPage.class),
            entry("ExportListItem", Export.class),
            entry("ExportMetaResponse", ExportMeta.class),
            entry("Schema", ParquetSchema.class),
            entry("ProjectedColumn", ParquetColumn.class),
            entry("ProxyStreamEntry", ProxyEvent.class),
            entry("AnonymizerStreamEntry", AnonymizerEvent.class),
            entry("TorrentStreamEntry", TorrentEvent.class),
            entry("TorrentFileEntry", TorrentFile.class),
            entry("TorrentPeerEntry", TorrentPeer.class),
            entry("JA4TStreamEntry", Ja4tEvent.class),
            entry("HoneypotMeta", HeliosMeta.class),
            entry("HoneypotHTTPStreamEntry", HeliosHttpEvent.class),
            entry("HoneypotHTTPSStreamEntry", HeliosHttpsEvent.class),
            entry("HoneypotDNSStreamEntry", HeliosDnsEvent.class),
            entry("HoneypotADBStreamEntry", HeliosAdbEvent.class),
            entry("TLSClientHello", TlsClientHello.class),
            entry("TLSCodeName", TlsCodeName.class),
            entry("TLSExtension", TlsExtension.class),
            entry("IpBatchResponse", SynthientClient.BatchResponse.class));

    /** Request bodies and error documents, which the SDK builds or parses without a model. */
    private static final Set<String> UNMODELED = Set.of("IpBatchRequest", "ProblemDetails", "ValidationProblemDetails");

    private static final Map<String, Class<?>> VALUE_SETS = Map.ofEntries(
            entry("NetworkInfo.type", NetworkType.class),
            entry("DeviceInfo.os", OperatingSystem.class),
            entry("DomainLookupResponse.status", DomainStatus.class),
            entry("ActivityStatsBlock.cadence", Cadence.class),
            entry("Organization.relation", Relation.class),
            entry("AccountInfoResponse.scopes", Scope.class),
            entry("ExportListItem.kind", ExportKind.class),
            entry("ExportMetaResponse.kind", ExportKind.class),
            entry("ProjectedColumn.type", ParquetType.class),
            entry("ProxyStreamEntry.type", ProxyType.class),
            entry("AnonymizerStreamEntry.type", ProxyType.class),
            entry("JA4TStreamEntry.type", ProxyType.class));

    private static JsonNode readSpec() {
        try {
            return Json.MAPPER.readTree(Files.readAllBytes(Path.of(System.getProperty("synthient.spec"))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode schemas() {
        return SPEC.path("components").path("schemas");
    }

    @Test
    void usesTheSpecServer() {
        assertEquals(
                SynthientClient.DEFAULT_BASE_URL,
                SPEC.path("servers").get(0).path("url").asString());
    }

    @Test
    void everySchemaIsModeled() {
        Set<String> covered = new TreeSet<>(MODELS.keySet());
        covered.addAll(UNMODELED);
        assertEquals(new TreeSet<>(schemas().propertyNames()), covered);
    }

    @TestFactory
    Stream<DynamicTest> modelsMatchTheirSchemas() {
        return MODELS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(model -> DynamicTest.dynamicTest(
                        model.getKey(),
                        () -> checkRecord(model.getValue(), schemas().get(model.getKey()), model.getKey())));
    }

    private static void checkRecord(Class<?> type, JsonNode schema, String location) {
        assertTrue(type.isRecord(), location + " is not a record");
        Set<String> required = new HashSet<>();
        schema.path("required").forEach(name -> required.add(name.asString()));
        JsonNode properties = schema.path("properties");
        Map<String, RecordComponent> components = new HashMap<>();
        for (RecordComponent component : type.getRecordComponents()) {
            JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);
            assertNotNull(property, location + "." + component.getName() + " has no @JsonProperty");
            components.put(property.value(), component);
        }
        assertEquals(new TreeSet<>(properties.propertyNames()), new TreeSet<>(components.keySet()), location);
        components.forEach((name, component) -> {
            String where = location + "." + name;
            JsonNode property = properties.get(name);
            boolean isRequired = required.contains(name);
            boolean nullable = !isRequired || property.path("nullable").asBoolean(false);
            assertEquals(
                    isRequired,
                    component.getAccessor().getAnnotation(JsonProperty.class).required(),
                    where + " required");
            assertEquals(
                    nullable, component.getAnnotatedType().isAnnotationPresent(Nullable.class), where + " @Nullable");
            assertFalse(nullable && component.getType().isPrimitive(), where + " is primitive but may be absent");
            checkType(component.getGenericType(), property, name, where);
        });
    }

    private static void checkType(Type type, JsonNode schema, String name, String where) {
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asString().replace("#/components/schemas/", "");
            assertEquals(MODELS.get(ref), type, where);
            return;
        }
        if (schema.has("allOf")) {
            assertEquals(1, schema.get("allOf").size(), where);
            checkType(type, schema.get("allOf").get(0), name, where);
            return;
        }
        String format = schema.path("format").asString("");
        Set<Type> allowed = switch (schema.path("type").asString("")) {
            case "string" ->
                switch (name) {
                    case "date" -> Set.of(LocalDate.class);
                    default -> format.equals("date-time") ? Set.of(Instant.class) : Set.of(String.class);
                };
            case "integer" ->
                switch (name) {
                    case "timestamp", "last_seen", "created_at", "date" -> Set.of(Instant.class);
                    case "resets_in" -> Set.of(Duration.class);
                    // The spec says int64, but the live Helios streams send values above Long.MAX_VALUE.
                    case "tunnel_id" -> Set.of(BigInteger.class);
                    default ->
                        format.equals("int64")
                                ? Set.of(long.class, Long.class)
                                : Set.of(int.class, Integer.class, long.class, Long.class);
                };
            case "number" -> Set.of(double.class, Double.class);
            case "boolean" -> Set.of(boolean.class, Boolean.class);
            case "array" -> {
                ParameterizedType list = assertInstanceOf(ParameterizedType.class, type, where);
                assertEquals(List.class, list.getRawType(), where);
                checkType(list.getActualTypeArguments()[0], schema.get("items"), "", where + "[]");
                yield Set.of(type);
            }
            case "object" -> {
                if (schema.has("additionalProperties")) {
                    ParameterizedType map = assertInstanceOf(ParameterizedType.class, type, where);
                    assertEquals(Map.class, map.getRawType(), where);
                    assertEquals(String.class, map.getActualTypeArguments()[0], where);
                    checkType(map.getActualTypeArguments()[1], schema.get("additionalProperties"), "", where + "{}");
                } else {
                    checkRecord(assertInstanceOf(Class.class, type, where), schema, where);
                }
                yield Set.of(type);
            }
            default -> fail(where + " has an unknown type " + schema);
        };
        assertTrue(allowed.contains(type), where + " is " + type.getTypeName() + ", expected one of " + allowed);
    }

    @Test
    void valueSetsMatchTheSpec() {
        Map<String, Set<String>> spec = new TreeMap<>();
        for (Map.Entry<String, JsonNode> schema : schemas().properties()) {
            for (Map.Entry<String, JsonNode> property :
                    schema.getValue().path("properties").properties()) {
                JsonNode values = enumValues(
                        property.getValue().has("items") ? property.getValue().get("items") : property.getValue());
                if (values.size() >= 2) {
                    Set<String> set = new TreeSet<>();
                    values.forEach(value -> set.add(value.asString()));
                    spec.put(schema.getKey() + "." + property.getKey(), set);
                }
            }
        }
        assertEquals(spec.keySet(), new TreeSet<>(VALUE_SETS.keySet()));
        spec.forEach((key, values) -> assertEquals(values, constants(VALUE_SETS.get(key)), key));
    }

    private static JsonNode enumValues(JsonNode schema) {
        return schema.has("enum") ? schema.get("enum") : schema.path("x-extensible-enum");
    }

    private static Set<String> constants(Class<?> holder) {
        Set<String> values = new TreeSet<>();
        for (Field field : holder.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    values.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return values;
    }

    @Test
    void everyOperationIsCovered() throws Exception {
        try (TestServer server = new TestServer(exchange -> TestServer.json(exchange, 404, "{}"));
                SynthientClient client = server.client()) {
            List<Runnable> calls = new ArrayList<>(List.of(
                    client::account,
                    client::health,
                    () -> client.lookupIp("1.1.1.1"),
                    () -> client.lookupIps(List.of("1.1.1.1")),
                    () -> client.lookupDomain("example.com")));
            for (Feed<?> feed : TestServer.feeds(client)) {
                String hour = ExportId.hourly(LocalDate.of(2026, 5, 3), 7);
                calls.add(feed::listExports);
                calls.add(feed::stream);
                calls.add(() -> feed.exportMeta(ExportId.LATEST));
                calls.add(() -> feed.exportMeta(hour));
                calls.add(() -> feed.downloadUrl(ExportId.LATEST));
                calls.add(() -> feed.downloadUrl(hour));
            }
            for (Runnable call : calls) {
                assertThrows(ApiException.class, call::run);
            }

            Map<String, List<String>> templates = new TreeMap<>();
            for (Map.Entry<String, JsonNode> path : SPEC.path("paths").properties()) {
                for (String method : path.getValue().propertyNames()) {
                    templates
                            .computeIfAbsent(method.toUpperCase(Locale.ROOT), m -> new ArrayList<>())
                            .add(path.getKey());
                }
            }
            Set<String> spec = templates.entrySet().stream()
                    .flatMap(e -> e.getValue().stream().map(path -> e.getKey() + " " + path))
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<String> covered = new TreeSet<>();
            for (TestServer.Request request : server.requests) {
                String path = request.path().replaceFirst("^/api/v4", "");
                String template = templates.getOrDefault(request.method(), List.of()).stream()
                        .filter(candidate -> path.matches(candidate.replaceAll("\\{[^/]+}", "[^/]+")))
                        .min(Comparator.comparingInt(candidate -> candidate.split("\\{", -1).length))
                        .orElseThrow(() -> new AssertionError("no operation for " + request.method() + " " + path));
                covered.add(request.method() + " " + template);
            }
            assertEquals(53, spec.size());
            assertEquals(spec, covered);
        }
    }
}
