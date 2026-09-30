package com.synthient.examples;

import com.synthient.SynthientClient;
import com.synthient.model.Export;
import com.synthient.model.ExportKind;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Downloads the newest daily proxies export to {@code proxies.parquet} and checks its SHA-256. Needs the {@code
 * PROXY_FEEDS} scope. Run with {@code ./gradlew -q :examples:downloadExport}.
 */
public final class DownloadExportExample {
    public static void main(String[] args) throws Exception {
        try (SynthientClient client = new SynthientClient()) {
            Export daily = null;
            for (Export export : client.proxies().allExports()) {
                System.out.printf(
                        "%-16s %-6s %10d rows %12d bytes%n",
                        export.id(), export.kind(), export.rowCount(), export.sizeBytes());
                if (export.kind().equals(ExportKind.DAILY)) {
                    daily = export;
                    break;
                }
            }
            if (daily == null) {
                System.err.println("no daily export found");
                System.exit(1);
            }

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            Path file = Path.of("proxies.parquet");
            try (InputStream body = new DigestInputStream(client.proxies().download(daily.id()), sha256);
                    OutputStream out = Files.newOutputStream(file)) {
                body.transferTo(out);
            }
            if (!HexFormat.of().formatHex(sha256.digest()).equals(daily.checksum())) {
                System.err.println("checksum mismatch");
                System.exit(1);
            }
            System.out.println("saved " + daily.id() + " to " + file);
        }
    }

    private DownloadExportExample() {}
}
