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
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import io.camunda.connector.doc.splitting.DefaultDocumentSplitterFactory;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import io.camunda.connector.model.operation.EmbedDocumentSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.mime.MediaType;

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
    if (embedRequest.documentSource() == EmbedDocumentSource.CamundaDocument) {
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
    } else {
      return splitter.split(
          DocumentLoader.load(
              new DocumentSource() {

                @Override
                public InputStream inputStream() throws IOException {
                  return IOUtils.toInputStream(
                      embedRequest.documentSourceFromProcessVariable(), "UTF-8");
                }

                @Override
                public Metadata metadata() {
                  return Metadata.from(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
                }
              },
              new TextDocumentParser()));
    }
  }
}
