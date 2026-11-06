/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * How the documents reach the model: as a reference, or with their content. A document has the
 * delivery {@link #REFERENCE} unless the document delivery is set to {@link #CONTENT}.
 */
public enum DocumentDelivery {
  /** The model sees the document handle only; a tool can add the content when it asks for it. */
  @JsonProperty("reference")
  REFERENCE,
  /**
   * The agent adds the content for the model, as if a tool had loaded the document it asks for, for
   * every document that passes the content guard.
   */
  @JsonProperty("content")
  CONTENT;

  /**
   * The extension property of a tool activity that sets the document delivery of the documents the
   * tool returns.
   */
  public static final String EXTENSION_PROPERTY = "io.camunda.agenticai.documents.delivery";

  /**
   * The delivery named by the value of the extension property {@link #EXTENSION_PROPERTY}, ignoring
   * case and surrounding blanks; a blank or absent value is {@link #REFERENCE}.
   *
   * @throws IllegalArgumentException if the value names no delivery; the message lists the allowed
   *     values
   */
  public static DocumentDelivery fromExtension(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return REFERENCE;
    }
    final var name = value.strip();
    return Arrays.stream(values())
        .filter(delivery -> delivery.name().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() ->
            new IllegalArgumentException(
                "The document delivery '%s' is not allowed, use one of: %s"
                    .formatted(
                        value,
                        Arrays.stream(values())
                            .map(delivery -> delivery.name().toLowerCase(Locale.ROOT))
                            .collect(Collectors.joining(", ")))));
  }
}
