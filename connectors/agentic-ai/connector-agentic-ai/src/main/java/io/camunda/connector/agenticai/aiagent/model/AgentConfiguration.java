/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Static per-invocation configuration. Built once from AgentExecutionContext at the start of each
 * handler invocation; does not change mid-conversation, with the exception of {@link
 * #toolDefinitions()}, which the handler fills in once tool resolution has run (see {@link
 * #withToolDefinitions}) with the tools of the process and the available local tools, and which
 * then becomes the authoritative source of the current tool list for the rest of the invocation.
 *
 * <p>{@link #features()} are the features of the agent instance (see {@link
 * AgentContext#features()}); the handler sets them from the agent context. They are not part of the
 * {@link #fingerprint()}.
 */
public record AgentConfiguration(
    ChatModelConfiguration chatModel,
    SystemPromptConfiguration systemPrompt,
    UserPromptConfiguration userPrompt,
    @Nullable MemoryConfiguration memory,
    @Nullable LimitsConfiguration limits,
    @Nullable EventHandlingConfiguration events,
    @Nullable ResponseConfiguration response,
    List<ToolDefinition> toolDefinitions,
    Set<String> features) {

  public static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;
  public static final int DEFAULT_MAX_MODEL_CALLS = 10;

  public AgentConfiguration {
    features = Set.copyOf(features);
  }

  public AgentConfiguration(
      ChatModelConfiguration chatModel,
      SystemPromptConfiguration systemPrompt,
      UserPromptConfiguration userPrompt,
      @Nullable MemoryConfiguration memory,
      @Nullable LimitsConfiguration limits,
      @Nullable EventHandlingConfiguration events,
      @Nullable ResponseConfiguration response,
      List<ToolDefinition> toolDefinitions) {
    this(
        chatModel,
        systemPrompt,
        userPrompt,
        memory,
        limits,
        events,
        response,
        toolDefinitions,
        Set.of());
  }

  public AgentConfiguration(
      ChatModelConfiguration chatModel,
      SystemPromptConfiguration systemPrompt,
      UserPromptConfiguration userPrompt,
      @Nullable MemoryConfiguration memory,
      @Nullable LimitsConfiguration limits,
      @Nullable EventHandlingConfiguration events,
      @Nullable ResponseConfiguration response) {
    this(
        chatModel, systemPrompt, userPrompt, memory, limits, events, response, List.of(), Set.of());
  }

  /** Returns {@code true} if the agent instance was created with the given feature. */
  public boolean supports(String feature) {
    return features.contains(feature);
  }

  /** Returns {@code true} if the agent instance was created with the documents feature. */
  public boolean supportsDocuments() {
    return supports(AgentContext.FEATURE_DOCUMENTS);
  }

  /**
   * Returns {@code true} if the agent instance was created with the local tools feature or with the
   * documents feature, which implies it.
   */
  public boolean supportsLocalTools() {
    return supports(AgentContext.FEATURE_LOCAL_TOOLS) || supportsDocuments();
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
        chatModel,
        systemPrompt,
        userPrompt,
        memory,
        limits,
        events,
        response,
        toolDefinitions,
        features);
  }

  /** Returns a copy carrying the given features of the agent instance. */
  public AgentConfiguration withFeatures(Set<String> features) {
    return new AgentConfiguration(
        chatModel,
        systemPrompt,
        userPrompt,
        memory,
        limits,
        events,
        response,
        toolDefinitions,
        features);
  }

  /**
   * A stable fingerprint over the configuration attributes the engine records on a {@code
   * CONFIGURATION} agent-instance history item (model, provider, system prompt, model-call limit,
   * tools): equal attributes always yield an equal fingerprint. Used both to detect whether the
   * agent instance's recorded configuration is stale, and as that history item's id, so a repeat
   * send with unchanged content dedups for free. The result is the SHA-256 digest as lower-case
   * hex.
   *
   * <p>The tool definitions enter the hash in a canonical form that does not depend on the
   * iteration order of the maps they hold (a definition rebuilt through {@link
   * ToolDefinition#builder()} or a JSON round trip holds its schema in an immutable map of
   * unspecified order). Per tool, in list order: {@code name}, {@code description} and {@code
   * inputSchema}, followed by the {@code documentDelivery} when the tool has one. The rules for the
   * schema:
   *
   * <ul>
   *   <li>maps are written with their keys sorted, recursively, including maps nested in lists;
   *   <li>lists keep their order, so the order of the tools and of values such as {@code required}
   *       is significant;
   *   <li>numbers are compared by value: every number is read as a decimal, an integral value (any
   *       {@link Integer}, {@link Long}, {@link Short}, {@link Byte}, or an integral {@link
   *       Double}, {@link Float}, {@link java.math.BigDecimal}) is written as that integer, so
   *       {@code 5L}, {@code 5} and {@code 5.0} agree, and any other value as its plain decimal
   *       string without trailing zeros;
   *   <li>{@code null} is written explicitly; strings are written length-prefixed so that no value
   *       can run into its neighbour.
   * </ul>
   *
   * Nothing is sorted or changed on the definitions themselves, so the order of what is sent to the
   * model and what is persisted stays as authored.
   */
  public String fingerprint() {
    var canonical = new StringBuilder();
    appendCanonical(canonical, chatModel.model());
    appendCanonical(canonical, chatModel.descriptiveProvider());
    appendCanonical(canonical, systemPrompt.prompt());
    appendCanonical(canonical, maxModelCalls());
    canonical.append('[');
    for (var tool : toolDefinitions) {
      canonical.append('{');
      appendCanonical(canonical, tool.name());
      appendCanonical(canonical, tool.description());
      appendCanonical(canonical, tool.inputSchema());
      // written only when set, so the fingerprint of a tool without it is the one it always had
      if (tool.documentDelivery() != null) {
        appendCanonical(canonical, tool.documentDelivery().name());
      }
      canonical.append('}');
    }
    canonical.append(']');
    try {
      var digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static void appendCanonical(StringBuilder out, @Nullable Object value) {
    if (value == null) {
      out.append("null,");
    } else if (value instanceof Map<?, ?> map) {
      var sorted = new TreeMap<String, Object>();
      map.forEach((key, entry) -> sorted.put(String.valueOf(key), entry));
      out.append('{');
      sorted.forEach(
          (key, entry) -> {
            appendCanonical(out, key);
            appendCanonical(out, entry);
          });
      out.append("},");
    } else if (value instanceof Collection<?> collection) {
      out.append('[');
      collection.forEach(entry -> appendCanonical(out, entry));
      out.append("],");
    } else if (value instanceof Number number) {
      out.append('n').append(canonicalNumber(number)).append(',');
    } else if (value instanceof Boolean bool) {
      out.append(bool ? "true," : "false,");
    } else {
      var text = value.toString();
      out.append('s').append(text.length()).append(':').append(text).append(',');
    }
  }

  private static String canonicalNumber(Number number) {
    BigDecimal decimal;
    if (number instanceof BigDecimal bigDecimal) {
      decimal = bigDecimal;
    } else if (number instanceof BigInteger bigInteger) {
      decimal = new BigDecimal(bigInteger);
    } else if (number instanceof Integer
        || number instanceof Long
        || number instanceof Short
        || number instanceof Byte) {
      decimal = BigDecimal.valueOf(number.longValue());
    } else {
      try {
        decimal = new BigDecimal(number.toString());
      } catch (NumberFormatException e) {
        // NaN and infinities have no decimal form
        return number.toString();
      }
    }
    decimal = decimal.stripTrailingZeros();
    return decimal.scale() <= 0 ? decimal.toBigInteger().toString() : decimal.toPlainString();
  }
}
