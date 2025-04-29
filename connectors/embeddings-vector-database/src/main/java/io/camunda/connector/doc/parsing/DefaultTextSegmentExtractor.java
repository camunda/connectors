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
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import io.camunda.connector.doc.splitting.DefaultDocumentSplitterFactory;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class DefaultTextSegmentExtractor {

  private static final String FILENAME_METADATA_KEY = "filename";

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
                            Map.of(FILENAME_METADATA_KEY, camundaDoc.metadata().getFileName()));
                      }
                    },
                    // Apache Tika includes metadata, such as
                    // real content type, encoding
                    new ApacheTikaDocumentParser(true)))
        .map(splitter::split)
        .flatMap(List::stream)
        .toList();
  }
}
