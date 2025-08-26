/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.doc.parsing;

import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import io.camunda.connector.doc.parsing.source.CamundaDocumentSource;
import io.camunda.connector.doc.parsing.source.PlainTextAsDocumentSource;
import io.camunda.connector.doc.splitting.DefaultDocumentSplitterFactory;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import io.camunda.connector.model.operation.EmbedDocumentSource;
import java.util.List;

public class DefaultTextSegmentExtractor {

  private final DefaultDocumentSplitterFactory documentSplitterFactory;

  public DefaultTextSegmentExtractor() {
    this(new DefaultDocumentSplitterFactory());
  }

  public DefaultTextSegmentExtractor(DefaultDocumentSplitterFactory documentSplitterFactory) {
    this.documentSplitterFactory = documentSplitterFactory;
  }

  public List<TextSegment> fromRequest(EmbeddingsVectorDBRequest request) {
    final var embedRequest = (EmbedDocumentOperation) request.vectorDatabaseConnectorOperation();
    final var splitter =
        documentSplitterFactory.createDocumentSplitter(embedRequest.documentSplitter());
    if (embedRequest.documentSource() == EmbedDocumentSource.CamundaDocument) {
      return embedRequest.newDocuments().stream()
          .map(
              camundaDoc ->
                  DocumentLoader.load(
                      new CamundaDocumentSource(camundaDoc),
                      // Apache Tika includes metadata, such as
                      // real content type, encoding
                      new ApacheTikaDocumentParser(true)))
          .map(splitter::split)
          .flatMap(List::stream)
          .toList();
    } else {
      return splitter.split(
          DocumentLoader.load(
              new PlainTextAsDocumentSource(embedRequest.documentSourceFromProcessVariable()),
              new TextDocumentParser()));
    }
  }
}
