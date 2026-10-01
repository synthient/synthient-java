package com.synthient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synthient.model.AnonymizerEvent;
import com.synthient.model.HeliosDnsEvent;
import com.synthient.model.HeliosHttpEvent;
import com.synthient.model.HeliosHttpsEvent;
import com.synthient.model.ProxyEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TimestampTest {
    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1, 999999999999L, 1780000000123L})
    void decodesMillisecondsWithoutGuessingFromMagnitude(long timestamp) {
        byte[] anonymizer = ("{\"timestamp\":" + timestamp + "}").getBytes(UTF_8);
        byte[] helios = """
                {"timestamp":%d,"tunnel_id":1,"domain":"example.com","port":80,"protocol":"http",
                 "meta":{"proxy_ip":"198.51.100.4","provider":"acme"}}
                """.formatted(timestamp).getBytes(UTF_8);
        Instant expected = Instant.ofEpochMilli(timestamp);
        assertEquals(expected, Json.decode(anonymizer, AnonymizerEvent.class).timestamp());
        assertEquals(expected, Json.decode(helios, HeliosHttpEvent.class).timestamp());
        assertEquals(expected, Json.decode(helios, HeliosHttpsEvent.class).timestamp());
        assertEquals(expected, Json.decode(helios, HeliosDnsEvent.class).timestamp());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 1780000000, 1000000000000L})
    void decodesSecondsWithoutGuessingFromMagnitude(long timestamp) {
        byte[] body = ("{\"timestamp\":" + timestamp + "}").getBytes(UTF_8);
        assertEquals(
                Instant.ofEpochSecond(timestamp),
                Json.decode(body, ProxyEvent.class).timestamp());
    }

    @Test
    void timestampUnitsAlsoApplyToConsumerMappers() {
        AnonymizerEvent event =
                JsonMapper.builder().build().readValue("{\"timestamp\":1780000000123}", AnonymizerEvent.class);
        assertEquals(Instant.ofEpochMilli(1780000000123L), event.timestamp());
    }

    @Test
    void rejectsOutOfRangeSeconds() {
        byte[] body = ("{\"timestamp\":" + Long.MAX_VALUE + "}").getBytes(UTF_8);
        assertThrows(SynthientException.class, () -> Json.decode(body, ProxyEvent.class));
    }
}
