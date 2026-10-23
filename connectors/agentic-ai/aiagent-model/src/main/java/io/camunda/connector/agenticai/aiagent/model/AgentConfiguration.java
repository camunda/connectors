/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Static per-invocation configuration. Built once from AgentExecutionContext at the start of each
 * handler invocation; does not change mid-conversation, with the exception of {@link
 * #toolDefinitions()}, which the handler fills in once tool resolution has run (see {@link
 * #withToolDefinitions}) with the tools of the process and the enabled runtime tools, and which
 * then becomes the authoritative source of the current tool list for the rest of the invocation.
 */
public record AgentConfiguration(
    ChatModelConfiguration chatModel,
    SystemPromptConfiguration systemPrompt,
    UserPromptConfiguration userPrompt,
    @Nullable MemoryConfiguration memory,
    @Nullable LimitsConfiguration limits,
    @Nullable EventHandlingConfiguration events,
    @Nullable ResponseConfiguration response,
    List<ToolDefinition> toolDefinitions) {

  public static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;
  public static final int DEFAULT_MAX_MODEL_CALLS = 10;

  private static final ObjectMapper FINGERPRINT_MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
          .defaultPropertyInclusion(JsonInclude.Value.ALL_ALWAYS)
          .build();

  public AgentConfiguration(
      ChatModelConfiguration chatModel,
      SystemPromptConfiguration systemPrompt,
      UserPromptConfiguration userPrompt,
      @Nullable MemoryConfiguration memory,
      @Nullable LimitsConfiguration limits,
      @Nullable EventHandlingConfiguration events,
      @Nullable ResponseConfiguration response) {
    this(chatModel, systemPrompt, userPrompt, memory, limits, events, response, List.of());
  }

  public int contextWindowSize() {
    return Optional.ofNullable(memory)
        .map(MemoryConfiguration::contextWindowSize)
        .orElse(DEFAULT_CONTEXT_WINDOW_SIZE);
  }

  public int maxModelCalls() {
    return Optional.ofNullable(limits)
        .map(LimitsConfiguration::maxModelCalls)
        .orElse(DEFAULT_MAX_MODEL_CALLS);
  }

  /** Returns a copy carrying the given tool definitions in place of the current ones. */
  public AgentConfiguration withToolDefinitions(List<ToolDefinition> toolDefinitions) {
    return new AgentConfiguration(
        chatModel, systemPrompt, userPrompt, memory, limits, events, response, toolDefinitions);
  }

  /**
   * A stable fingerprint over the configuration attributes the engine records on a {@code
   * CONFIGURATION} agent-instance history item (model, provider, system prompt, model-call limit,
   * tools): equal attributes always yield an equal fingerprint. Used both to detect whether the
   * agent instance's recorded configuration is stale, and as that history item's id, so a repeat
   * send with unchanged content dedups for free. The result is the SHA-256 digest as lower-case
   * hex.
   *
   * <p>The attributes enter the hash as JSON with the entries of every map sorted by key, because
   * the iteration order of the immutable maps that hold a tool input schema is unspecified and
   * differs between JVMs. The order of the parameters, of the tools and of list values such as
   * {@code required} is part of what the model sees, so it stays significant: sorting the keys of
   * the schema would lose the parameter order, so the hash input carries it as a list. The input is
   * written and read once before it is hashed, so a number hashes the same whether the tool
   * definition is fresh or was loaded from the process variable. The document delivery of a tool is
   * part of the attributes, so a tool that gets another delivery changes the fingerprint.
   *
   * <p>The mapper is configured for the fingerprint alone and does not inherit the settings of the
   * connectors mapper: a date or a duration is written as ISO text, never as a timestamp, so that
   * it does not turn into a decimal number.
   */
  public String fingerprint() {
    try {
      final var input =
          Arrays.asList(
              chatModel.model(),
              chatModel.descriptiveProvider(),
              systemPrompt.prompt(),
              maxModelCalls(),
              toolDefinitions.stream().map(AgentConfiguration::fingerprintInput).toList());
      final var canonical =
          FINGERPRINT_MAPPER.readValue(FINGERPRINT_MAPPER.writeValueAsBytes(input), Object.class);
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(FINGERPRINT_MAPPER.writeValueAsBytes(canonical)));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to calculate the configuration fingerprint: the configuration cannot be serialized",
          e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(
          "Failed to calculate the configuration fingerprint: SHA-256 is not available", e);
    }
  }

  private static List<@Nullable Object> fingerprintInput(ToolDefinition tool) {
    final var input =
        new ArrayList<@Nullable Object>(
            Arrays.asList(
                tool.name(),
                tool.description(),
                tool.inputSchema(),
                parameterOrder(tool.inputSchema())));
    if (tool.kind() == ToolKind.RUNTIME) {
      input.add(tool.kind().name());
    }
    // a definition without a document delivery hashes as it does without the property
    if (tool.documentDelivery() != null) {
      input.add(tool.documentDelivery());
    }
    return input;
  }

  private static List<String> parameterOrder(Map<String, Object> inputSchema) {
    return inputSchema.get("properties") instanceof Map<?, ?> properties
        ? properties.keySet().stream().map(String::valueOf).toList()
        : List.of();
  }
}
