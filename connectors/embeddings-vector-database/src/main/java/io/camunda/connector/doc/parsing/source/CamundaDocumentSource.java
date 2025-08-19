/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.doc.parsing.source;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import io.camunda.connector.api.document.Document;
import java.io.InputStream;

public class CamundaDocumentSource implements DocumentSource {

  private static final String FILENAME_METADATA_KEY = "filename";

  private final Document document;

  public CamundaDocumentSource(final Document document) {
    this.document = document;
  }

  @Override
  public InputStream inputStream() {
    return document.asInputStream();
  }

  @Override
  public Metadata metadata() {
    return Metadata.from(FILENAME_METADATA_KEY, document.metadata().getFileName());
  }
}
