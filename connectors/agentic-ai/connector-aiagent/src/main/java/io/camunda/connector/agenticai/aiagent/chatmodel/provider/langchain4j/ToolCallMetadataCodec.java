/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import java.util.Map;

/**
 * Carries the data that a provider requires to be echoed back verbatim on the next request, such as
 * the thought signatures of Gemini 3, through {@code ToolCall#metadata()}. LangChain4j returns this
 * data as attributes of the {@code AiMessage} and expects it again as attributes of the replayed
 * {@code AiMessage}.
 *
 * <p>The {@link ChatMessageConverter} owns the namespace. It persists the data that the {@link
 * Writer} returns under {@link #provider()}, and gives the {@link Reader} only the entry of that
 * provider. A provider can therefore never read the data of another provider, for example after a
 * process instance migration switches the provider.
 *
 * @param provider the provider ID that the data is persisted under, the provider of the chat model
 *     configuration
 * @param writer extracts the data to persist from the attributes of a response
 * @param reader restores the attributes of a replayed message from the persisted data
 */
public record ToolCallMetadataCodec(String provider, Writer writer, Reader reader) {

  /** Extracts the data to persist for one tool call from the attributes of a response. */
  @FunctionalInterface
  public interface Writer {

    /**
     * Called with the full, flat attributes of the {@code AiMessage} (never empty) and the ID of
     * the tool call that is converted.
     *
     * @return the data to persist for that tool call, empty if there is none
     */
    Map<String, Object> write(String toolCallId, Map<String, Object> aiMessageAttributes);
  }

  /** Restores the attributes of a replayed message from the data that {@link Writer} persisted. */
  @FunctionalInterface
  public interface Reader {

    /**
     * Called with the persisted data of one tool call (never empty, untyped since it round-tripped
     * through a process variable).
     *
     * @return the entries to put on the attributes of the replayed {@code AiMessage}, keyed the way
     *     LangChain4j expects. The entries of all tool calls of the message are merged into one
     *     attribute map, so make each key unique per tool call, for example by including the tool
     *     call ID
     */
    Map<String, Object> read(String toolCallId, Map<?, ?> providerMetadata);
  }

  /** A codec that carries no data. */
  public static ToolCallMetadataCodec none(String provider) {
    return new ToolCallMetadataCodec(
        provider, (toolCallId, attributes) -> Map.of(), (id, md) -> Map.of());
  }
}
