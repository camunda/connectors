/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The stable id of a document the agent knows. The id is the handle a document is addressed by in
 * the {@code <doc id="..." />} tag the model reads, in the messages of the conversation and in the
 * arguments of a tool call. It is the id of the document in its store for a Camunda document and
 * derived from the document for any other.
 *
 * <p>Serializes as a bare JSON string.
 *
 * @param value the id, never blank
 */
public record DocumentId(@JsonValue String value) {

  public DocumentId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Document id cannot be null or empty");
    }
  }

  @JsonCreator
  public static DocumentId of(String value) {
    return new DocumentId(value);
  }

  /** The id as the model reads it. */
  @Override
  public String toString() {
    return value;
  }
}
