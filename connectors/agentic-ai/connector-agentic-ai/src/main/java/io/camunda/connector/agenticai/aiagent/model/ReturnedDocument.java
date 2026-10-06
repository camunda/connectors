/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A document the agent returns with its final response, found in {@link AgentResponse#documents()}
 * under the key the model chose.
 *
 * @param document the Camunda document reference of the document, resolved from the registry
 * @param description what the model said about the document, if anything
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnedDocument(Map<String, Object> document, @Nullable String description) {

  public ReturnedDocument {
    Objects.requireNonNull(document, "document must not be null");
  }
}
