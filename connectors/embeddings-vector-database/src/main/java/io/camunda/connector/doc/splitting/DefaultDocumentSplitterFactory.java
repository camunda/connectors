/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.doc.splitting;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import io.camunda.connector.model.embedding.splitter.NoopDocumentSplitter;
import io.camunda.connector.model.embedding.splitter.RecursiveDocumentSplitter;
import java.util.List;

public class DefaultDocumentSplitterFactory {

  public DocumentSplitter createDocumentSplitter(
      io.camunda.connector.model.embedding.splitter.DocumentSplitter fromTemplate) {
    return switch (fromTemplate) {
      case RecursiveDocumentSplitter recursiveDocumentSplitter ->
          documentSplitterRecursive(recursiveDocumentSplitter);
      case NoopDocumentSplitter ignored -> noopDocumentSplitter();
    };
  }

  private DocumentSplitter documentSplitterRecursive(RecursiveDocumentSplitter fromTemplate) {
    // recursive is just chain of command pattern, starting from
    // "by-paragraph" -> "by-line" -> etc
    return DocumentSplitters.recursive(
        fromTemplate.maxSegmentSizeInChars(), fromTemplate.maxOverlapSizeInChars());
  }

  private DocumentSplitter noopDocumentSplitter() {
    // depending on the document and ML scientist recommendation
    // sometimes it's better not to split text at all
    return document -> List.of(document.toTextSegment());
  }
}
