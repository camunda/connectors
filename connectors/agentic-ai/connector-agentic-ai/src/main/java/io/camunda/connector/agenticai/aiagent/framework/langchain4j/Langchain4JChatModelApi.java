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
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import java.util.List;

/** Bridges the existing LangChain4J framework adapter behind the {@link ChatModelApi} SPI. */
public class Langchain4JChatModelApi implements ChatModelApi {

  /**
   * Uniform conservative capability profile for every provider routed through this bridge.
   * Deliberately NOT resolved via {@code ModelCapabilitiesResolver} — that resolver's own
   * conservative default is {@code [TEXT]} for user messages, which would regress today's
   * document-in-user-message support ({@code DocumentToContentConverterImpl} already accepts text,
   * image and PDF content). This constant is distinct from that resolver default; do not conflate
   * them.
   */
  private static final ModelCapabilities BRIDGE_CAPABILITIES =
      ModelCapabilities.builder()
          .userMessageModalities(List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT))
          // The bridge embeds no document natively in a tool result — its
          // ToolCallConverterImpl.contentElementAsString serializes every DocumentContent as a JSON
          // document reference regardless of MIME type, so every tool-result document must take the
          // synthetic <doc/> fallback (CapabilityAwareToolCallResultStrategy).
          .toolResultModalities(List.of())
          .assistantMessageModalities(List.of(Modality.TEXT))
          .build();

  private final Langchain4JAiFrameworkAdapter adapter;

  public Langchain4JChatModelApi(Langchain4JAiFrameworkAdapter adapter) {
    this.adapter = adapter;
  }

  @Override
  public ChatModelResult call(ChatModelRequest request) {
    final var response =
        adapter.executeMeasuringTime(request.executionContext(), request.snapshot());
    return new ChatModelResult.Completed(response.assistantMessage(), response.metrics());
  }

  @Override
  public ModelCapabilities capabilities() {
    return BRIDGE_CAPABILITIES;
  }
}
