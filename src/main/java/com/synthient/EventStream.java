package com.synthient;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import okio.BufferedSource;
import okio.Okio;
import org.jspecify.annotations.Nullable;

/**
 * An open connection to a real-time feed, read as a sequence of events. Close it to disconnect, ideally
 * with try-with-resources:
 *
 * {@snippet :
 * try (EventStream<ProxyEvent> events = client.proxies().stream()) {
 *     for (ProxyEvent event : events) {
 *         System.out.println(event.ip() + " " + event.provider());
 *     }
 * }
 * }
 *
 * <p>The server ends a stream after up to 30 minutes, and iteration then ends normally. The API has no
 * cursor, so events sent between two connections are lost; callers that want a continuous feed reconnect
 * in a loop, pausing only after a failure. A lost connection throws {@link ApiConnectionException},
 * and an event that cannot be decoded throws {@link SynthientException}; either ends the stream.
 *
 * <p>{@link #iterator()} may be called once. {@link #close()} may be called from another thread, which ends
 * the iteration.
 *
 * @param <T> the event type
 */
public final class EventStream<T> implements Iterable<T>, Closeable {
    private static final int MAX_EVENT_SIZE = 16 << 20;

    private final InputStream body;
    private final BufferedSource source;
    private final Class<T> type;
    private boolean iterated;
    private volatile boolean closed;

    EventStream(InputStream body, Class<T> type) {
        this.body = body;
        this.source = Okio.buffer(Okio.source(body));
        this.type = type;
    }

    /**
     * Returns the events of the stream. Reading blocks until the server sends the next event.
     *
     * @throws IllegalStateException if called more than once
     */
    @Override
    public Iterator<T> iterator() {
        if (iterated) {
            throw new IllegalStateException("an event stream can only be iterated once");
        }
        iterated = true;
        return new Iterator<>() {
            private @Nullable T next;

            @Override
            public boolean hasNext() {
                if (closed) {
                    next = null;
                    return false;
                }
                if (next == null) {
                    next = readEvent();
                }
                return next != null;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                T event = Objects.requireNonNull(next);
                next = null;
                return event;
            }
        };
    }

    private @Nullable T readEvent() {
        if (closed) {
            return null;
        }
        try {
            for (byte[] line = readLine(); line != null; line = readLine()) {
                if (!isBlank(line)) {
                    return Json.decode(line, type);
                }
            }
            close();
            return null;
        } catch (IOException e) {
            if (closed) {
                return null;
            }
            close();
            throw Transport.connectionFailure(e);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    /** Returns the next line without its newline, or null at the end of the stream. */
    private byte @Nullable [] readLine() throws IOException {
        long newline = source.indexOf((byte) '\n', 0, MAX_EVENT_SIZE + 1);
        if (newline == -1) {
            if (source.getBuffer().size() > MAX_EVENT_SIZE) {
                throw new SynthientException("stream event exceeds " + MAX_EVENT_SIZE + " bytes");
            }
            return source.exhausted() ? null : source.readByteArray();
        }
        byte[] line = source.readByteArray(newline);
        source.skip(1);
        return line;
    }

    private static boolean isBlank(byte[] bytes) {
        for (byte b : bytes) {
            if (b != ' ' && b != '\t' && b != '\r') {
                return false;
            }
        }
        return true;
    }

    /** Disconnects from the feed. Safe to call more than once and from any thread. */
    @Override
    public void close() {
        closed = true;
        Transport.closeQuietly(body);
    }
}
