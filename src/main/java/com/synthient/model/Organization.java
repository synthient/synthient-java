package com.synthient.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The organization an API key belongs to.
 *
 * @param id the organization ID
 * @param name the organization's name
 * @param relation the account's role in it, one of {@link Relation}
 */
public record Organization(
        @JsonProperty(value = "id", required = true) String id,
        @JsonProperty(value = "name", required = true) String name,
        @JsonProperty(value = "relation", required = true) String relation) {

    public Organization {
        requireNonNull(id, "id");
        requireNonNull(name, "name");
        requireNonNull(relation, "relation");
    }
}
