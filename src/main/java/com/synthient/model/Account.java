package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The account that owns the API key.
 *
 * @param scopes the scopes the key grants, each one of {@link Scope}
 * @param lookupQuota the organization's lookup credits
 * @param apiKey the key used for the request, or null when the API omits it
 */
public record Account(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("email") String email,
        @JsonProperty("organization") Organization organization,
        @JsonProperty("scopes") List<String> scopes,
        @JsonProperty("lookup_quota") LookupQuota lookupQuota,
        @JsonProperty("api_key") @Nullable AccountApiKey apiKey) {

    public Account {
        requireNonNull(firstName, "firstName");
        requireNonNull(lastName, "lastName");
        requireNonNull(email, "email");
        requireNonNull(organization, "organization");
        scopes = List.copyOf(requireNonNull(scopes, "scopes"));
        requireNonNull(lookupQuota, "lookupQuota");
    }
}
