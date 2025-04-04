/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.modes;

import static io.camunda.connector.model.modes.EmbeddingsVectorMode.MODE_EMBED_DOCUMENT;
import static io.camunda.connector.model.modes.EmbeddingsVectorMode.MODE_RETRIEVE_DOCUMENT;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EmbedDocumentMode.class, name = MODE_EMBED_DOCUMENT),
  @JsonSubTypes.Type(value = RetrieveDocumentMode.class, name = MODE_RETRIEVE_DOCUMENT)
})
@TemplateDiscriminatorProperty(
    label = "Mode",
    group = "mode",
    name = "type",
    defaultValue = "retrieveDocument",
    description = "Select purpose of the Connector")
public sealed interface EmbeddingsVectorMode permits EmbedDocumentMode, RetrieveDocumentMode {
  String MODE_EMBED_DOCUMENT = "embedDocument";
  String MODE_RETRIEVE_DOCUMENT = "retrieveDocument";
}
