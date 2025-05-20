/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers.gcp;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    defaultImpl = VertexRequestConfiguration.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = VertexRequestConfiguration.class, name = "vertex"),
  @JsonSubTypes.Type(value = DocumentAiRequestConfiguration.class, name = "documentAi"),
})
@NotNull
@TemplateDiscriminatorProperty(
    label = "Request configuration",
    group = "configuration",
    name = "type")
public sealed interface GcpRequestConfiguration
    permits VertexRequestConfiguration, DocumentAiRequestConfiguration {}
