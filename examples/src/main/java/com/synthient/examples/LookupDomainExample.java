package com.synthient.examples;

import com.synthient.SynthientClient;
import com.synthient.model.DomainEvent;
import com.synthient.model.DomainInfo;
import com.synthient.model.SubdomainCount;

/** Looks up proxy traffic to a domain. Run with {@code ./gradlew -q :examples:lookupDomain --args=example.com}. */
public final class LookupDomainExample {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: lookupDomain <domain>");
            System.exit(2);
        }
        try (SynthientClient client = new SynthientClient()) {
            DomainInfo domain = client.lookupDomain(args[0], 5);
            System.out.println("domain: " + domain.domain() + " " + domain.status());
            System.out.printf(
                    "events: %d in 24h, %d in 30d%n",
                    domain.stats().events24h(), domain.stats().totalEvents30d());
            System.out.printf(
                    "unique IPs: %d in 24h, %d in 30d%n",
                    domain.uniqueIps().value24h(), domain.uniqueIps().value30d());
            for (SubdomainCount subdomain : domain.topSubdomains()) {
                System.out.printf("subdomain: %s (%d events)%n", subdomain.subdomain(), subdomain.count());
            }
            for (DomainEvent event : domain.recentEvents()) {
                System.out.printf(
                        "event: %s %s -> %s:%d%n",
                        event.timestamp(), event.sourceIp(), event.targetSubdomain(), event.port());
            }
        }
    }

    private LookupDomainExample() {}
}
