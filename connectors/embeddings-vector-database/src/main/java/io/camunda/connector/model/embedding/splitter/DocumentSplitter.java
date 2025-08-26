/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.splitter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "splitterType")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = RecursiveDocumentSplitter.class,
      name = RecursiveDocumentSplitter.RECURSIVE_DOCUMENT_SPLITTER),
  @JsonSubTypes.Type(
      value = NoopDocumentSplitter.class,
      name = NoopDocumentSplitter.NOOP_DOCUMENT_SPLITTER)
})
@TemplateDiscriminatorProperty(
    name = "splitterType",
    id = "documentSplitter",
    group = "document",
    defaultValue = RecursiveDocumentSplitter.RECURSIVE_DOCUMENT_SPLITTER,
    label = "Document splitting strategy")
@TemplateSubType(label = "Document splitter", id = "documentSplitter")
public sealed interface DocumentSplitter permits RecursiveDocumentSplitter, NoopDocumentSplitter {}
