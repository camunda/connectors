/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A response document as persisted in the agent context: a document the model chose with the {@code
 * c8__return_document} tool for the final response, under the key it chose.
 *
 * @param document the {@link DocumentHandle#idFor stable id} of the document in the registry
 * @param description what the model said about the document, if anything
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponseDocumentSelection(String document, @Nullable String description) {

  public ResponseDocumentSelection {
    Objects.requireNonNull(document, "document must not be null");
  }
}
