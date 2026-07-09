/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.ANTHROPIC_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesOverride;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import org.jspecify.annotations.Nullable;

/**
 * Wire-format-first chat-model configuration surfaced by the v2 connectors (the #7224 target
 * shape). Only the {@code anthropic} and {@code openai} members exist in the pilot; other providers
 * are additive later. Polymorphism is by the {@code type} discriminator; the concrete member owns
 * its backend-conditional authentication.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = AnthropicChatModel.class, name = ANTHROPIC_ID)})
@TemplateDiscriminatorProperty(
    label = "Provider",
    group = "provider",
    name = "type",
    description = "Specify the LLM provider to use.",
    defaultValue = ANTHROPIC_ID)
public sealed interface LlmProviderConfiguration permits AnthropicChatModel {

  /** Discriminator string identifying the provider (e.g. {@code anthropic}, {@code openai}). */
  String type();

  /**
   * The model id / deployment the request targets. Named {@code modelId()} (not {@code model()})
   * because concrete members expose a structured {@code model} record component.
   */
  String modelId();

  /**
   * The backend discriminator (e.g. {@code direct}, {@code bedrock}, {@code compatible}). Named
   * {@code backendType()} (not {@code backend()}) because concrete members expose a structured
   * {@code backend} record component.
   */
  @Nullable String backendType();

  /**
   * Optional sparse per-element capability override, the highest-precedence overlay for the matrix.
   * Raw-nullable here; the resolver boundary (Task 5) wraps it in {@link java.util.Optional}.
   */
  @Nullable ModelCapabilitiesOverride capabilityOverride();
}
