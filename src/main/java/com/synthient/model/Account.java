package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The account that owns the API key.
 *
 * @param firstName the account holder's first name
 * @param lastName the account holder's last name
 * @param email the account holder's email
 * @param organization the organization the key belongs to
 * @param scopes the scopes the key grants, each one of {@link Scope}
 * @param lookupQuota the organization's lookup credits
 * @param apiKey the key used for the request, or null when the API omits it
 */
public record Account(
        @JsonProperty(value = "first_name", required = true) String firstName,
        @JsonProperty(value = "last_name", required = true) String lastName,
        @JsonProperty(value = "email", required = true) String email,

        @JsonProperty(value = "organization", required = true)
        Organization organization,

        @JsonProperty(value = "scopes", required = true) List<String> scopes,

        @JsonProperty(value = "lookup_quota", required = true)
        LookupQuota lookupQuota,

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
