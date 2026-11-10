/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A document selected for the final response, with the agent document the selection points to.
 *
 * @param key the key the process finds the document by, see {@link
 *     ResponseDocumentSelection#KEY_PATTERN}
 * @param document the selected document
 * @param description a description of the document, if any
 */
public record ResponseDocument(String key, AgentDocument document, @Nullable String description) {

  public ResponseDocument {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(document, "document must not be null");
  }
}
