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
      name = EmbedDocumentOperation.OPERATION_EMBED_DOCUMENT),
  @JsonSubTypes.Type(
      value = RetrieveDocumentOperation.class,
      name = RetrieveDocumentOperation.OPERATION_RETRIEVE_DOCUMENT)
})
@TemplateDiscriminatorProperty(
    label = "Operation",
    group = "operation",
    name = "operationType",
    defaultValue = RetrieveDocumentOperation.OPERATION_RETRIEVE_DOCUMENT,
    description = "Select operation")
public sealed interface VectorDatabaseConnectorOperation
    permits EmbedDocumentOperation, RetrieveDocumentOperation {}
