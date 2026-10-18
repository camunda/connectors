/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.LinkedSystemPromptResolver.ResolvedLinkedSystemPrompt;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Prototype (unsupported): builds {@code {"evidence": {...}}} OBJECT content blocks that are
 * appended to agent instance history items so that Operate can show where a turn's inputs came from
 * (linked prompt, saved credential, server tools, sandbox). Never records secret values.
 */
public final class AgentHistoryEvidence {

  public static final String EVIDENCE_KEY = "evidence";
  public static final String SANDBOX_PROPERTY = "sandbox";
  private static final String OPENAI_CUSTOM_CREDENTIAL_TEMPLATE =
      "io.camunda:agentic-ai-openai-custom-credential:1";
  private static final Pattern EXIT_CODE = Pattern.compile("exit_code:\\s*(-?\\d+)");

  private AgentHistoryEvidence() {}

  public static Map<String, Object> wrap(Map<String, Object> evidence) {
    return Map.of(EVIDENCE_KEY, evidence);
  }

  public static Map<String, Object> configuration(
      ChatModelConfiguration provider, @Nullable ResolvedLinkedSystemPrompt linkedPrompt) {
    final Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("kind", "configuration");
    if (linkedPrompt != null) {
      final Map<String, Object> prompt = new LinkedHashMap<>();
      prompt.put("type", "linked");
      prompt.put("promptId", linkedPrompt.promptId());
      prompt.put("binding", linkedPrompt.binding());
      prompt.put("version", linkedPrompt.version());
      prompt.put("resourceKey", String.valueOf(linkedPrompt.resourceKey()));
      evidence.put("systemPrompt", prompt);
    } else {
      evidence.put("systemPrompt", Map.of("type", "inline"));
    }
    evidence.put("credential", credential(provider));
    evidence.put("serverTools", Map.of("configured", configuredServerTools(provider)));
    return evidence;
  }

  private static Map<String, Object> credential(ChatModelConfiguration provider) {
    final Map<String, Object> credential = new LinkedHashMap<>();
    if (provider instanceof OpenAiChatModelConfiguration openAi
        && openAi.openai().backend() instanceof OpenAiCustomBackend custom
        && custom.custom().openAiCustomCredential() != null) {
      credential.put("mode", "saved credential");
      credential.put("template", OPENAI_CUSTOM_CREDENTIAL_TEMPLATE);
      credential.put("boundTo", "provider.openai.backend.custom.openAiCustomCredential");
      credential.put("secretFields", List.of("apiKey"));
    } else {
      credential.put("mode", "inline");
    }
    return credential;
  }

  private static List<Object> configuredServerTools(ChatModelConfiguration provider) {
    if (provider instanceof OpenAiChatModelConfiguration openAi
        && openAi.openai().api() instanceof OpenAiCompletionsApi completions
        && completions.completions() != null
        && completions.completions().serverTools() != null) {
      return completions.completions().serverTools().stream()
          .map(tool -> (Object) ("openrouter:" + tool.type().name().toLowerCase()))
          .toList();
    }
    return List.of();
  }

  /** Server tool citations recorded by the response converter in the assistant metadata. */
  public static @Nullable Map<String, Object> serverToolUse(AssistantMessage message) {
    final List<Object> citations = new ArrayList<>();
    collectCitations(message.metadata(), citations);
    if (citations.isEmpty()) {
      return null;
    }
    final Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("kind", "serverTools");
    evidence.put("used", List.of("openrouter:web_search"));
    evidence.put("citations", citations);
    return evidence;
  }

  private static void collectCitations(@Nullable Object value, List<Object> citations) {
    if (value instanceof Map<?, ?> map) {
      map.forEach(
          (key, nested) -> {
            if ("citations".equals(key) && nested instanceof List<?> list) {
              citations.addAll(list);
            } else {
              collectCitations(nested, citations);
            }
          });
    }
  }

  /**
   * Evidence for a sandbox gateway tool result: the sandbox handle from the agent context, the
   * operation and its input, the exit code, and whether the in-process fallback answered instead.
   */
  public static @Nullable Map<String, Object> sandbox(
      @Nullable ToolCall toolCall,
      @Nullable String elementId,
      @Nullable Object sandboxState,
      List<Content> content) {
    final boolean fallback = isFallback(content);
    final boolean sandboxTool = toolCall != null && toolCall.name().startsWith("sandbox_");
    final boolean sandboxElement =
        sandboxState instanceof Map<?, ?> state
            && elementId != null
            && elementId.equals(state.get("elementId"));
    if (!fallback && !sandboxTool && !sandboxElement) {
      return null;
    }
    final Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("kind", "sandbox");
    evidence.put("provider", fallback ? "in-process fallback (no sandbox)" : "daytona");
    evidence.put("fallback", fallback);
    if (!fallback && sandboxState instanceof Map<?, ?> state) {
      putIfPresent(evidence, "sandboxId", state.get("handle"));
      putIfPresent(evidence, "workDir", state.get("workDir"));
    }
    if (toolCall != null) {
      evidence.put("operation", toolCall.name());
      final var arguments = toolCall.arguments();
      if (arguments != null) {
        for (final var key : List.of("command", "script", "code", "path", "document")) {
          if (arguments.get(key) != null) {
            evidence.put(key, arguments.get(key));
          }
        }
        if (arguments.get("id") != null) {
          evidence.put("documentId", arguments.get("id"));
        }
      }
    }
    putIfPresent(evidence, "exitCode", exitCode(content));
    return evidence;
  }

  private static boolean isFallback(List<Content> content) {
    return content.stream()
        .anyMatch(
            c ->
                c instanceof ObjectContent object
                    && object.content() instanceof Map<?, ?> map
                    && Boolean.TRUE.equals(map.get("sandboxFallback")));
  }

  private static @Nullable Object exitCode(List<Content> content) {
    for (final var c : content) {
      if (c instanceof TextContent text) {
        final var matcher = EXIT_CODE.matcher(text.text());
        if (matcher.find()) {
          return Integer.parseInt(matcher.group(1));
        }
      } else if (c instanceof ObjectContent object && object.content() instanceof Map<?, ?> map) {
        for (final var key : List.of("exit_code", "exitCode")) {
          if (map.get(key) != null) {
            return map.get(key);
          }
        }
      }
    }
    return null;
  }

  private static void putIfPresent(Map<String, Object> map, String key, @Nullable Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
