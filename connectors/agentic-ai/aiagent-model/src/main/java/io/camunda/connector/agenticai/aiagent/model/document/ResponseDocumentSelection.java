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
 * A response document as persisted in the agent context: a document selected for the final
 * response, under the key the selection was made with. The agent context keeps the selections in
 * the order they were made; a selection under a key that is already used replaces that selection in
 * place.
 *
 * @param key the key the process finds the document by
 * @param document the stable id of the document in the registry
 * @param description a description of the document, if any
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponseDocumentSelection(
    String key, DocumentId document, @Nullable String description) {

  public ResponseDocumentSelection {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(document, "document must not be null");
  }
}
