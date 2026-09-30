/**
 * Response models of the Synthient API.
 *
 * <p>Models are immutable records named after the API's JSON fields. Fields the API always sends are
 * non-null, and a response missing one fails to decode. Optional fields are {@code null} when the API omits
 * them. Timestamps are decoded to {@link java.time.Instant}.
 *
 * <p>Fields with a documented value set, such as {@link com.synthient.model.Network#type()}, are strings so
 * that values the API adds later still decode. Holder classes such as {@link com.synthient.model.NetworkType}
 * list the known values.
 *
 * <p>Canonical constructors exist for tests and may gain parameters in minor releases as the API adds
 * fields.
 */
@NullMarked
package com.synthient.model;

import org.jspecify.annotations.NullMarked;
