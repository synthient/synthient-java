package com.synthient.examples;

import com.synthient.ApiException;
import com.synthient.EventStream;
import com.synthient.SynthientClient;
import com.synthient.SynthientException;
import com.synthient.model.ProxyEvent;

/**
 * Prints the real-time proxies feed until interrupted, reconnecting whenever the stream ends. Needs the {@code
 * PROXY_FIREHOSE} scope, and stops if the API rejects the key or the scope is missing. Run with {@code ./gradlew
 * -q :examples:stream}.
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
                    if (e instanceof ApiException api && (api.statusCode() == 401 || api.statusCode() == 403)) {
                        throw e;
                    }
                    System.err.println("stream: " + e);
                    Thread.sleep(2000);
                }
            }
        }
    }

    private StreamExample() {}
}
