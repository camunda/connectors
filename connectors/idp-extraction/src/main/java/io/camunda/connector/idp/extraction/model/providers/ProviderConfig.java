/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import jakarta.validation.constraints.NotNull;

/**
 * @deprecated Legacy IDP extraction provider model, used only by {@link
 *     io.camunda.connector.idp.extraction.ExtractionConnectorFunction}. The structured /
 *     unstructured / classification connectors use the {@code request.common} provider model
 *     instead. Retained for backwards compatibility; no removal currently planned.
 */
@Deprecated(since = "8.9")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = AwsProvider.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsProvider.class, name = "aws"),
  @JsonSubTypes.Type(value = AzureProvider.class, name = "azure"),
  @JsonSubTypes.Type(value = GcpProvider.class, name = "gcp"),
  @JsonSubTypes.Type(value = OpenAiProvider.class, name = "openai")
})
@NotNull
@TemplateDiscriminatorProperty(label = "Hyperscaler providers", group = "provider", name = "type")
public sealed interface ProviderConfig
    permits AwsProvider, AzureProvider, GcpProvider, OpenAiProvider {}
