/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration.CUSTOM_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import org.jspecify.annotations.Nullable;

/**
 * Wire-format-first chat-model configuration surfaced by the v2 connectors. Each provider member
 * nests its fields under a single provider-named component (mirroring the v1 {@code
 * ProviderConfiguration}), so generated element-template property ids are namespaced ({@code
 * provider.anthropic.*} / {@code provider.openai.*}) and the interface accessors below compute from
 * the nested data without colliding with a record component. Polymorphism is by the {@code type}
 * discriminator; the concrete member owns its backend-conditional authentication.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = CustomProviderConfiguration.class, name = CUSTOM_ID)})
@TemplateDiscriminatorProperty(
    label = "Provider",
    group = "provider",
    name = "type",
    description = "Specify the LLM provider to use.",
    defaultValue = CUSTOM_ID)
public sealed interface ProviderConfiguration extends ChatModelConfiguration
    permits CustomProviderConfiguration {

  /** Discriminator string identifying the provider (e.g. {@code anthropic}, {@code openai}). */
  @Override
  String provider();

  /** The model id / deployment the request targets. */
  @Override
  String model();

  /** The backend discriminator (e.g. {@code direct}, {@code bedrock}, {@code compatible}). */
  default @Nullable String backend() {
    return null;
  }
}
