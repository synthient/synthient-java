package com.synthient.examples;

import com.synthient.ApiException;
import com.synthient.SynthientClient;
import com.synthient.model.Intelligence;
import com.synthient.model.IpInfo;
import com.synthient.model.Provider;

/** Looks up one IP address. Run with {@code ./gradlew -q :examples:lookupIp --args=8.8.8.8}. */
public final class LookupIpExample {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: lookupIp <ip>");
            System.exit(2);
        }
        try (SynthientClient client = new SynthientClient()) {
            IpInfo info;
            try {
                info = client.lookupIp(args[0]);
            } catch (ApiException e) {
                if (e.statusCode() == 402) {
                    System.err.println("out of lookup credits");
                    System.exit(1);
                }
                throw e;
            }

            System.out.println("ip: " + info.ip());
            if (info.network() != null) {
                System.out.printf(
                        "network: %s (AS%d, %s)%n",
                        info.network().isp(),
                        info.network().asn(),
                        info.network().type());
            }
            if (info.location() != null) {
                System.out.printf(
                        "location: %s, %s%n",
                        info.location().city(), info.location().country());
            }
            Intelligence intelligence = info.intelligence();
            if (intelligence == null) {
                System.out.println("no intelligence available");
                return;
            }
            System.out.println("risk score: " + intelligence.riskScore());
            System.out.println("categories: " + String.join(", ", intelligence.categories()));
            for (Provider provider : intelligence.providers()) {
                System.out.printf(
                        "provider: %s (%s), last seen %s%n", provider.name(), provider.type(), provider.lastSeen());
            }
        }
    }

    private LookupIpExample() {}
}
