/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * LangChain4j bridge factory: produces a {@link Langchain4JChatModelApi} for one specific {@link
 * ProviderConfiguration} subtype using the matching {@link ChatModelProvider} bean. One factory
 * bean per discriminator — the provider type, configuration class, and {@link ChatModelProvider}
 * are wired explicitly per bean in {@code AgenticAiLangchain4JFrameworkConfiguration}, so there is
 * no {@code switch} on provider type inside the factory.
 *
 * <p>Public so customers can compose it from their own {@link ChatModelApiFactory} bean — e.g. to
 * wire a LangChain4j-supported provider we don't ship by passing in their own {@link
 * ChatModelProvider} alongside the framework's converter beans.
 */
public class Langchain4JChatModelApiFactory<C extends ProviderConfiguration>
    implements ChatModelApiFactory<C> {

  public static final String API_FAMILY = "langchain4j";

  private final String providerType;
  private final Class<C> configurationType;
  private final ChatModelProvider<C> chatModelProvider;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;

  public Langchain4JChatModelApiFactory(
      String providerType,
      Class<C> configurationType,
      ChatModelProvider<C> chatModelProvider,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    this.providerType = providerType;
    this.configurationType = configurationType;
    this.chatModelProvider = chatModelProvider;
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
  }

  @Override
  public String providerType() {
    return providerType;
  }

  @Override
  public String apiFamily() {
    return API_FAMILY;
  }

  @Override
  public Class<C> configurationType() {
    return configurationType;
  }

  @Override
  public ChatModelApi create(C configuration) {
    final var chatModel = chatModelProvider.createChatModel(configuration);
    return new Langchain4JChatModelApi(
        chatModel, chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter);
  }
}
