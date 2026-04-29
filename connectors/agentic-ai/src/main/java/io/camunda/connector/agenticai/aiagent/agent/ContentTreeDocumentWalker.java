/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Recursively walks an arbitrary content tree and collects {@link Document} instances. Handles
 * {@link Document}, {@link Map}, {@link Collection}, and {@code Object[]}; all other types are
 * skipped.
 *
 * <p>This is the default extraction strategy used by {@link
 * io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler#extractDocuments} and the fallback
 * for tool call results not managed by any gateway handler. It is intentionally public so that
 * gateway handler implementations whose typed content wraps raw user-generated subtrees (e.g.
 * arbitrary maps from a downstream system) can delegate to it for those subtrees.
 */
public final class ContentTreeDocumentWalker {

  public static final ContentTreeDocumentWalker INSTANCE = new ContentTreeDocumentWalker();

  public List<Document> extractDocumentsFromContent(Object content) {
    if (content == null) {
      return List.of();
    }

    final var documents = new ArrayList<Document>();
    collectDocuments(content, documents);
    return documents;
  }

  private void collectDocuments(Object node, List<Document> documents) {
    if (node == null) {
      return;
    }

    switch (node) {
      case Document document -> documents.add(document);
      case Map<?, ?> map -> map.values().forEach(value -> collectDocuments(value, documents));
      case Collection<?> collection ->
          collection.forEach(element -> collectDocuments(element, documents));
      case Object[] array -> {
        for (Object element : array) {
          collectDocuments(element, documents);
        }
      }
      default -> {
        // scalars and other types - nothing to extract
      }
    }
  }
}
