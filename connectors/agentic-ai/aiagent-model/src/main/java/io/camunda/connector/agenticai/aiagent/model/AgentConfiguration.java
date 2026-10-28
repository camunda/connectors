/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Static per-invocation configuration. Built once from AgentExecutionContext at the start of each
 * handler invocation; does not change mid-conversation, with the exception of {@link
 * #toolDefinitions()}, which the handler fills in once tool resolution has run (see {@link
 * #withToolDefinitions}) and which then becomes the authoritative source of the current tool list
 * for the rest of the invocation.
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

  private static final ObjectMapper FINGERPRINT_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

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
   * <p>The attributes enter the hash as JSON. A tool definition holds its input schema in an
   * immutable map whose iteration order is unspecified and differs between JVMs. Schema map keys
   * are sorted recursively, except for {@code properties} maps, which retain parameter order. Tool
   * and list order (including {@code required}) is also retained. Decimal numbers are reduced to
   * the value they keep after a JSON round trip. Normalization only affects the hash input; the
   * tool definitions are unchanged.
   */
  public String fingerprint() {
    try {
      var json =
          FINGERPRINT_MAPPER.writeValueAsBytes(
              Arrays.asList(
                  chatModel.model(),
                  chatModel.descriptiveProvider(),
                  systemPrompt.prompt(),
                  maxModelCalls(),
                  toolDefinitions.stream().map(AgentConfiguration::fingerprintInput).toList()));
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("The configuration cannot be serialized", e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static List<@Nullable Object> fingerprintInput(ToolDefinition tool) {
    return Arrays.asList(
        tool.name(), tool.description(), normalizeSchema(tool.inputSchema(), SchemaContext.SCHEMA));
  }

  private enum SchemaContext {
    SCHEMA,
    NAMED_SCHEMAS,
    PROPERTIES,
    DATA
  }

  private static @Nullable Object normalizeSchema(@Nullable Object value, SchemaContext context) {
    if (value instanceof Map<?, ?> map) {
      final Map<String, @Nullable Object> normalized =
          context == SchemaContext.PROPERTIES ? new LinkedHashMap<>() : new TreeMap<>();
      map.forEach(
          (key, child) -> {
            final var childContext =
                context == SchemaContext.SCHEMA
                    ? switch ((String) key) {
                      case "properties" -> SchemaContext.PROPERTIES;
                      case "$defs",
                          "definitions",
                          "patternProperties",
                          "dependentSchemas",
                          "dependencies" ->
                          SchemaContext.NAMED_SCHEMAS;
                      case "default", "const", "enum", "examples" -> SchemaContext.DATA;
                      default -> SchemaContext.SCHEMA;
                    }
                    : context == SchemaContext.DATA ? SchemaContext.DATA : SchemaContext.SCHEMA;
            normalized.put((String) key, normalizeSchema(child, childContext));
          });
      return normalized;
    }
    if (value instanceof List<?> list) {
      final var childContext =
          context == SchemaContext.DATA ? SchemaContext.DATA : SchemaContext.SCHEMA;
      return list.stream().map(child -> normalizeSchema(child, childContext)).toList();
    }
    if (value instanceof BigDecimal decimal && decimal.scale() == 0) {
      return decimal.toBigIntegerExact();
    }
    if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) {
      final double number = Double.parseDouble(value.toString());
      return number == Math.rint(number) && Math.abs(number) < 0x1p53
          ? (Object) (long) number
          : number;
    }
    return value;
  }
}
