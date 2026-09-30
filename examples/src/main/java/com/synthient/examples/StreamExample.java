package com.synthient.examples;

import com.synthient.EventStream;
import com.synthient.SynthientClient;
import com.synthient.SynthientException;
import com.synthient.model.ProxyEvent;

/**
 * Prints the real-time proxies feed until interrupted, reconnecting whenever the stream ends. Needs the {@code
 * PROXY_FIREHOSE} scope. Run with {@code ./gradlew -q :examples:stream}.
 */
public final class StreamExample {
    public static void main(String[] args) throws InterruptedException {
        try (SynthientClient client = new SynthientClient()) {
            while (true) {
                try (EventStream<ProxyEvent> events = client.proxies().stream()) {
                    for (ProxyEvent event : events) {
                        System.out.println(event.timestamp() + " " + event.ip() + " " + event.provider() + " "
                                + event.type() + " " + event.countryCode());
                    }
                } catch (SynthientException e) {
                    System.err.println("stream: " + e);
                }
                // The server closes each stream after about 30 minutes. Pausing before reconnecting keeps
                // repeated failures under the connection rate limit.
                Thread.sleep(2000);
            }
        }
    }

    private StreamExample() {}
}
