/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.splitter;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateSubType(label = "Do not split", id = NoopDocumentSplitter.DOCUMENT_SPLITTER_NONE)
public record NoopDocumentSplitter() implements DocumentSplitter {
  @TemplateProperty(ignore = true)
  public static final String DOCUMENT_SPLITTER_NONE = "documentSplitterNone";
}
