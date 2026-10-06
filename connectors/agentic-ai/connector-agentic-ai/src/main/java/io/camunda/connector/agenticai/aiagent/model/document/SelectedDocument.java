/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A document the model selected for the final response with the {@code return_document} tool, as
 * persisted in the agent context under the key the model chose.
 *
 * @param document the {@link DocumentHandle#idFor stable id} of the document in the registry
 * @param description what the model said about the document, if anything
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SelectedDocument(String document, @Nullable String description) {

  public SelectedDocument {
    Objects.requireNonNull(document, "document must not be null");
  }
}
