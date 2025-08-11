/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.operation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "operationType")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = EmbedDocumentOperation.class,
      name = EmbedDocumentOperation.EMBED_DOCUMENT_OPERATION),
  @JsonSubTypes.Type(
      value = RetrieveDocumentOperation.class,
      name = RetrieveDocumentOperation.RETRIEVE_DOCUMENT_OPERATION)
})
@TemplateDiscriminatorProperty(
    label = "Operation",
    group = "operation",
    name = "operationType",
    defaultValue = RetrieveDocumentOperation.RETRIEVE_DOCUMENT_OPERATION,
    description = "Select operation")
public sealed interface VectorDatabaseConnectorOperation
    permits EmbedDocumentOperation, RetrieveDocumentOperation {}
