package com.synthient.examples;

import com.synthient.SynthientClient;
import com.synthient.model.IpInfo;
import java.util.List;

/** Looks up several IP addresses in one request. Run with {@code ./gradlew -q :examples:lookupIps --args="8.8.8.8 1.1.1.1"}. */
public final class LookupIpsExample {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: lookupIps <ip>...");
            System.exit(2);
        }
        try (SynthientClient client = new SynthientClient()) {
            for (IpInfo result : client.lookupIps(List.of(args))) {
                if (result.intelligence() == null) {
                    System.out.printf("%-39s no intelligence available%n", result.ip());
                    continue;
                }
                System.out.printf(
                        "%-39s risk %3d  %s%n",
                        result.ip(),
                        result.intelligence().riskScore(),
                        String.join(", ", result.intelligence().categories()));
            }
        }
    }

    private LookupIpsExample() {}
}
