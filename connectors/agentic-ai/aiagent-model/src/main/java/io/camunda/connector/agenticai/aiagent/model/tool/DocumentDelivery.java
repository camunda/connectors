/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** How a document reaches the model: as a reference, or with its content. */
public enum DocumentDelivery {
  /** The model sees the {@code <doc/>} tag only. */
  @JsonProperty("reference")
  REFERENCE,
  /** The agent adds the content of the document next to the tag when it calls the model. */
  @JsonProperty("content")
  CONTENT
}
