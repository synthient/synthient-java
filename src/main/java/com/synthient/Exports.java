package com.synthient;

import com.synthient.model.Export;
import com.synthient.model.ExportMeta;
import com.synthient.model.ExportPage;
import com.synthient.model.ExportVolume;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import org.jspecify.annotations.Nullable;

/**
 * The exports of a {@link Feed} or {@link Dataset}. The query parameter that picks one file of an export, {@code
 * variant} for feeds and {@code format} for datasets, is passed by name.
 */
final class Exports {
    private static final Pattern EXPORT_ID = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:/(\\d{1,2}))?");

    private final Transport transport;
    private final String path;
    private final boolean hourly;

    /** @param hourly whether there are hourly exports. Datasets have none, so their hourly IDs are invalid. */
    Exports(Transport transport, String path, boolean hourly) {
        this.transport = transport;
        this.path = path;
        this.hourly = hourly;
    }

    ExportPage page(@Nullable Integer limit, @Nullable String cursor, @Nullable String day, @Nullable String variant) {
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        HttpUrl.Builder url = transport.url(path + "/export");
        if (cursor != null) {
            url.addQueryParameter("cursor", cursor);
        }
        if (limit != null) {
            url.addQueryParameter("limit", limit.toString());
        }
        if (day != null) {
            url.addQueryParameter("day", day);
        }
        if (variant != null) {
            url.addQueryParameter("variant", variant);
        }
        return transport.call("GET", url.build(), null, Transport.Policy.TRANSIENT, ExportPage.class);
    }

    Iterable<Export> all(
            @Nullable Integer limit, @Nullable String start, @Nullable String day, @Nullable String variant) {
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        return () -> new Iterator<>() {
            private final Set<String> seen = new HashSet<>();
            private @Nullable String cursor = start;
            private Iterator<Export> page = Collections.emptyIterator();
            private boolean lastPage;

            @Override
            public boolean hasNext() {
                while (!page.hasNext()) {
                    if (lastPage) {
                        return false;
                    }
                    if (cursor != null && seen.contains(cursor)) {
                        throw new SynthientException("export listing returned a repeated cursor");
                    }
                    ExportPage result = page(limit, cursor, day, variant);
                    if (cursor != null) {
                        seen.add(cursor);
                    }
                    page = result.exports().iterator();
                    cursor = result.nextCursor();
                    lastPage = cursor == null;
                }
                return true;
            }

            @Override
            public Export next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return page.next();
            }
        };
    }

    ExportVolume volume(@Nullable Integer days) {
        HttpUrl.Builder url = transport.url(path + "/export/daily");
        if (days != null) {
            url.addQueryParameter("days", days.toString());
        }
        return transport.call("GET", url.build(), null, Transport.Policy.TRANSIENT, ExportVolume.class);
    }

    ExportMeta meta(String id, String parameter, @Nullable String value) {
        HttpUrl url = url(id, parameter, value).addPathSegment("meta").build();
        return transport.call("GET", url, null, Transport.Policy.TRANSIENT, ExportMeta.class);
    }

    URI downloadUrl(String id, String parameter, @Nullable String value) {
        return location(id, parameter, value).uri();
    }

    InputStream download(String id, String parameter, @Nullable String value) {
        Transport.RawResponse response = transport.fetchStorage(location(id, parameter, value));
        if (response.statusCode() == 200) {
            return response.body();
        }
        Transport.closeQuietly(response.body());
        throw ApiException.fromResponse(response.statusCode(), response.headers(), new byte[0], null);
    }

    /** @throws IllegalArgumentException if the ID is invalid */
    private HttpUrl.Builder url(String id, String parameter, @Nullable String value) {
        HttpUrl.Builder url = transport.url(path + "/export").addPathSegments(exportPath(id, hourly));
        if (value != null) {
            url.addQueryParameter(parameter, value);
        }
        return url;
    }

    /**
     * Asks the API for an export and returns the storage location it redirects to. The API's redirect is followed
     * by hand so that the API key is never sent to the storage host. The location is never included in errors
     * because it is a presigned URL.
     */
    private HttpUrl location(String id, String parameter, @Nullable String value) {
        Transport.RawResponse response = transport.send(
                "GET", url(id, parameter, value).build(), null, "application/json", Transport.Policy.TRANSIENT);
        int status = response.statusCode();
        if (status >= 400) {
            throw transport.apiError(response);
        }
        Transport.closeQuietly(response.body());
        // A 304 can only answer a conditional header the SDK never sends, such as one an interceptor added.
        if (status < 300 || status == 304) {
            throw new SynthientException("expected a redirect to the export, got status " + status);
        }
        String location = response.headers().get("Location");
        HttpUrl target =
                location == null || location.isBlank() ? null : response.url().resolve(location);
        if (target == null) {
            throw new SynthientException("export redirect has no valid location");
        }
        checkRedirect(response.url(), target);
        return target;
    }

    /**
     * Returns the path segments of an export ID, with the hour unpadded.
     *
     * @throws IllegalArgumentException if the ID is invalid
     */
    static String exportPath(String id, boolean hourly) {
        if (id.equals(ExportId.LATEST)) {
            return id;
        }
        Matcher matcher = EXPORT_ID.matcher(id);
        if (matcher.matches()) {
            try {
                LocalDate date = LocalDate.parse(matcher.group(1));
                String hour = matcher.group(2);
                if (hour == null) {
                    return date.toString();
                }
                int parsed = Integer.parseInt(hour);
                if (hourly && parsed <= 23) {
                    return date + "/" + parsed;
                }
            } catch (DateTimeParseException e) {
                // A day that does not exist, such as 2026-02-30, is invalid too.
            }
        }
        throw new IllegalArgumentException("invalid export ID \"" + id + "\"");
    }

    static void checkRedirect(HttpUrl from, HttpUrl target) {
        if (!target.username().isEmpty() || !target.password().isEmpty()) {
            throw new SynthientException("export redirect has an unsupported location");
        }
        if (from.isHttps() && !target.isHttps()) {
            throw new SynthientException("export redirect does not use https");
        }
    }
}
