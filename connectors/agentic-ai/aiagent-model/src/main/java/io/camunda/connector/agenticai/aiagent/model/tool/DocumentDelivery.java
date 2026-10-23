/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How the documents reach the model: as a reference, or with their content. A document has the
 * delivery {@link #REFERENCE} unless the document delivery is set to {@link #CONTENT}.
 */
public enum DocumentDelivery {
  /** The model sees the {@code <doc/>} tag only; a tool can add the content when it asks for it. */
  @JsonProperty("reference")
  REFERENCE,
  /**
   * The agent adds the content for the model, as if a tool had loaded the document it asks for, for
   * every document that passes the content guard.
   */
  @JsonProperty("content")
  CONTENT
}
