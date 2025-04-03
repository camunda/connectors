/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.doc.parsing;

import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import io.camunda.connector.doc.splitting.DefaultDocumentSplitterFactory;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

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
    return embedRequest.newDocuments().stream()
        .map(
            camundaDoc ->
                DocumentLoader.load(
                    new DocumentSource() {
                      @Override
                      public InputStream inputStream() {
                        return camundaDoc.asInputStream();
                      }

                      @Override
                      public Metadata metadata() {
                        return Metadata.from(
                            Map.of(
                                "filename",
                                camundaDoc
                                    .metadata()
                                    .getFileName())); // TODO: in v1 we agreed to put only filename
                        // as metadata; v2 has plans to refine this
                        // commitment
                      }
                    },
                    new TextDocumentParser())) // TODO: in v1 we agreed to have only simple text
        // parser; v2 plans to add PDF and other formats
        .map(splitter::split)
        .flatMap(List::stream)
        .toList();
  }
}
