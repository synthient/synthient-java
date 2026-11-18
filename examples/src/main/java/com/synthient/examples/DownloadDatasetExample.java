package com.synthient.examples;

import com.synthient.ExportId;
import com.synthient.SynthientClient;
import com.synthient.model.ExportFile;
import com.synthient.model.ExportFormat;
import com.synthient.model.ExportMeta;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;

/**
 * Downloads the newest geolocation dataset as a MaxMind DB to {@code geolocation.mmdb} and checks its SHA-256. Needs
 * the {@code GEOLOCATION_FEED} scope. Run with {@code ./gradlew -q :examples:downloadDataset}.
 */
public final class DownloadDatasetExample {
    public static void main(String[] args) throws Exception {
        try (SynthientClient client = new SynthientClient()) {
            ExportMeta meta = client.geolocation().exportMeta(ExportId.LATEST, ExportFormat.MMDB);
            String day = ExportId.daily(LocalDate.ofInstant(meta.date(), ZoneOffset.UTC));
            for (ExportFile file : meta.formats()) {
                System.out.printf("%-8s %12d bytes%n", file.format(), file.sizeBytes());
            }

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            Path file = Path.of("geolocation.mmdb");
            try (InputStream body =
                            new DigestInputStream(client.geolocation().download(day, ExportFormat.MMDB), sha256);
                    OutputStream out = Files.newOutputStream(file)) {
                body.transferTo(out);
            }
            if (!HexFormat.of().formatHex(sha256.digest()).equals(meta.checksum())) {
                System.err.println("checksum mismatch");
                System.exit(1);
            }
            System.out.println("saved " + day + " to " + file);
        }
    }

    private DownloadDatasetExample() {}
}
