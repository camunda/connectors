/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;

/** Bridges the existing LangChain4J framework adapter behind the {@link ChatModelApi} SPI. */
public class Langchain4JChatModelApi implements ChatModelApi {

  private final Langchain4JAiFrameworkAdapter adapter;

  public Langchain4JChatModelApi(Langchain4JAiFrameworkAdapter adapter) {
    this.adapter = adapter;
  }

  @Override
  public ChatModelResult call(ChatModelRequest request) {
    final var response =
        adapter.executeMeasuringTime(request.executionContext(), request.snapshot());
    return new ChatModelResult(response.assistantMessage(), response.metrics());
  }
}
