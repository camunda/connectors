/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapper;

/**
 * Translates the provider configuration of a request into the domain configuration of its chat
 * model. A request of the v1 flavor is first rewritten to its v2 equivalent, so there is one
 * translation per provider.
 */
public class ChatModelConfigurationTranslator {

  private final V1ToV2ProviderConfigurationMapper v1Mapper;

  public ChatModelConfigurationTranslator(V1ToV2ProviderConfigurationMapper v1Mapper) {
    this.v1Mapper = v1Mapper;
  }

  public ChatModelConfiguration translate(
      io.camunda.connector.agenticai.aiagent.request.v1.ProviderConfiguration provider) {
    return translate(v1Mapper.map(provider));
  }

  public ChatModelConfiguration translate(
      io.camunda.connector.agenticai.aiagent.request.v2.ProviderConfiguration provider) {
    return provider;
  }
}
