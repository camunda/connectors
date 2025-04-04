/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.splitter;

import static io.camunda.connector.model.embedding.splitter.DocumentSplitter.DOCUMENT_SPLITTER_BY_PARAGRAPH;
import static io.camunda.connector.model.embedding.splitter.DocumentSplitter.DOCUMENT_SPLITTER_BY_SENTENCE;
import static io.camunda.connector.model.embedding.splitter.DocumentSplitter.DOCUMENT_SPLITTER_RECURSIVE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DocumentSplitterRecursive.class, name = DOCUMENT_SPLITTER_RECURSIVE),
  @JsonSubTypes.Type(
      value = DocumentSplitterByParagraph.class,
      name = DOCUMENT_SPLITTER_BY_PARAGRAPH),
  @JsonSubTypes.Type(
      value = DocumentSplitterBySentence.class,
      name = DOCUMENT_SPLITTER_BY_SENTENCE),
})
@TemplateDiscriminatorProperty(
    name = "name",
    id = "documentSplitter",
    group = "document",
    defaultValue = DOCUMENT_SPLITTER_RECURSIVE,
    label = "Document splitter",
    description = "Select document splitter strategy")
@TemplateSubType(label = "Document splitter", id = "documentSplitter")
public sealed interface DocumentSplitter
    permits DocumentSplitterByParagraph, DocumentSplitterBySentence, DocumentSplitterRecursive {
  String DOCUMENT_SPLITTER_RECURSIVE = "DOCUMENT_SPLITTER_RECURSIVE";
  String DOCUMENT_SPLITTER_BY_SENTENCE = "DOCUMENT_SPLITTER_BY_SENTENCE";
  String DOCUMENT_SPLITTER_BY_PARAGRAPH = "DOCUMENT_SPLITTER_BY_PARAGRAPH";
}
