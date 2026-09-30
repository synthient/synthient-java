package com.synthient.examples;

import com.synthient.SynthientClient;
import com.synthient.model.Account;

/** Prints the account that owns the API key. Run with {@code ./gradlew -q :examples:account}. */
public final class AccountExample {
    public static void main(String[] args) {
        try (SynthientClient client = new SynthientClient()) {
            Account account = client.account();
            System.out.printf("%s %s <%s>%n", account.firstName(), account.lastName(), account.email());
            System.out.println("organization: " + account.organization().name());
            System.out.println("scopes: " + account.scopes());
            System.out.printf(
                    "credits: %d (resets in %s)%n",
                    account.lookupQuota().credits(), account.lookupQuota().resetsIn());
        }
    }

    private AccountExample() {}
}
