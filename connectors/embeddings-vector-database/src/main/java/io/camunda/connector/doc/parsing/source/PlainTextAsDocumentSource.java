/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.doc.parsing.source;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.mime.MediaType;

public class PlainTextAsDocumentSource implements DocumentSource {

  private final String text;

  public PlainTextAsDocumentSource(final String text) {
    this.text = text;
  }

  @Override
  public InputStream inputStream() {
    return IOUtils.toInputStream(text, StandardCharsets.UTF_8);
  }

  @Override
  public Metadata metadata() {
    return Metadata.from(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
  }
}
