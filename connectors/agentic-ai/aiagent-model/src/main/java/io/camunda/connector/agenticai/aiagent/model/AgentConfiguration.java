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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
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
   * immutable map whose top-level iteration order is unspecified and differs between JVMs, so the
   * top-level keys of the schema are sorted. Everything below keeps its order: the order of the
   * parameters, of the tools and of values such as {@code required} is part of what the model sees,
   * so a change of it changes the fingerprint. The document delivery of a tool is part of the
   * attributes, so a tool that gets another delivery changes the fingerprint.
   */
  public String fingerprint() {
    try {
      var json =
          FINGERPRINT_MAPPER.writeValueAsBytes(
              List.of(
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
    final var input =
        new ArrayList<@Nullable Object>(
            Arrays.asList(tool.name(), tool.description(), new TreeMap<>(tool.inputSchema())));
    // a definition without a document delivery hashes as it does without the property
    if (tool.documentDelivery() != null) {
      input.add(tool.documentDelivery());
    }
    return input;
  }
}
