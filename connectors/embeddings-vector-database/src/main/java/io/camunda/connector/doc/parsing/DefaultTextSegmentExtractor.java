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
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.doc.parsing.source.CamundaDocumentSource;
import io.camunda.connector.doc.parsing.source.PlainTextAsDocumentSource;
import io.camunda.connector.doc.splitting.DefaultDocumentSplitterFactory;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import io.camunda.connector.model.operation.EmbedDocumentSource;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class DefaultTextSegmentExtractor {

  private static final String NOTHING_TO_EMBED =
      "Nothing to embed: neither documents nor plain text were provided";

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
    if (usesPlainText(embedRequest)) {
      if (StringUtils.isBlank(embedRequest.documentSourceFromProcessVariable())) {
        throw new ConnectorInputException(NOTHING_TO_EMBED);
      }
      return splitter.split(
          DocumentLoader.load(
              new PlainTextAsDocumentSource(embedRequest.documentSourceFromProcessVariable()),
              new TextDocumentParser()));
    }
    if (isEmpty(embedRequest.newDocuments())) {
      throw new ConnectorInputException(NOTHING_TO_EMBED);
    }
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
  }

  /**
   * Element templates up to version 3 state the source explicitly, and are honoured as-is —
   * including the case where a document is left over in {@code newDocuments} from an earlier
   * selection. From version 4 on the source dropdown is gone (inline content replaced plain text),
   * so the path follows whichever input the template populated.
   */
  private static boolean usesPlainText(EmbedDocumentOperation embedRequest) {
    if (embedRequest.documentSource() != null) {
      return embedRequest.documentSource() == EmbedDocumentSource.PlainText;
    }
    return isEmpty(embedRequest.newDocuments());
  }

  private static boolean isEmpty(List<?> documents) {
    return documents == null || documents.isEmpty();
  }
}
