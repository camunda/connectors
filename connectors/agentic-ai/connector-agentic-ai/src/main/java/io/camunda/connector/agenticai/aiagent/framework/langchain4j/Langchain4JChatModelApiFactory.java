/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.ProviderChatModelApiConfiguration;

/**
 * Lowest-precedence bridge factory routing every built-in {@link ProviderChatModelApiConfiguration}
 * through the LangChain4J adapter. Serves every built-in provider configuration until native {@link
 * ChatModelApi} implementations are introduced and override it per provider.
 */
public class Langchain4JChatModelApiFactory implements ChatModelApiFactory {

  /** Low precedence: native implementations register below this to override the bridge. */
  static final int ORDER = 1000;

  private final Langchain4JAiFrameworkAdapter adapter;

  public Langchain4JChatModelApiFactory(Langchain4JAiFrameworkAdapter adapter) {
    this.adapter = adapter;
  }

  @Override
  public boolean supports(ChatModelApiConfiguration configuration) {
    return configuration instanceof ProviderChatModelApiConfiguration;
  }

  @Override
  public ChatModelApi create(ChatModelApiConfiguration configuration) {
    return new Langchain4JChatModelApi(adapter);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
