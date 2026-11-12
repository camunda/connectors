/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.api.document.Document;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A document the agent has not seen before: the live document of the connector SDK. It is the
 * content for a new document at the boundaries where documents enter the conversation (the user
 * prompt, events, the results of process tools and of a runtime tool that returns a new document),
 * for every document of an agent instance that does not track its documents, and the only document
 * block a chat model provider sees. A document the agent already knows is an {@link
 * AgentDocumentContent}.
 *
 * @param document the document
 * @param metadata the metadata of the content block
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentContent(
    Document document,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements PortableContent {
  public DocumentContent {
    if (document == null) {
      throw new IllegalArgumentException("Document cannot be null");
    }
  }

  public static DocumentContent documentContent(Document document) {
    return new DocumentContent(document, null);
  }
}
