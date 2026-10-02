/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the document parameters of an ad-hoc tool call. {@link
 * ToolDefinition#documentParameters} are JSON pointers into the input schema ({@code
 * /properties/doc}, {@code /properties/docs/items}, {@code /properties/req/properties/file}); the
 * arguments of a tool call mirror that structure, so each pointer addresses the argument values
 * that carry a document id. Every id is replaced by the Camunda document reference JSON of the
 * registered document.
 */
public final class DocumentParameterResolver {

  private DocumentParameterResolver() {}

  /** Outcome of resolving the document parameters of one tool call. */
  public sealed interface Resolution {

    /** All document ids are known; {@code arguments} carry the document references. */
    record Resolved(Map<String, Object> arguments) implements Resolution {}

    /** At least one document parameter is invalid; one message per invalid value. */
    record Invalid(List<String> problems) implements Resolution {
      public Invalid {
        problems = List.copyOf(problems);
      }
    }
  }

  /**
   * Resolves the document parameters of {@code toolCall}. A parameter that is missing or {@code
   * null} is left as is; a value that is not the id of a registered document (not a string, blank
   * or unknown) is reported as a problem naming the parameter.
   */
  public static Resolution resolve(
      ToolCall toolCall, ToolDefinition toolDefinition, DocumentRegistry registry) {
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();
    final var problems = new ArrayList<String>();
    Object resolved = arguments;
    for (final var pointer : toolDefinition.documentParameters()) {
      final var steps = parse(pointer);
      if (steps != null) {
        resolved = walk(resolved, steps, 0, "", registry, problems);
      }
    }
    if (!problems.isEmpty()) {
      return new Resolution.Invalid(problems);
    }
    @SuppressWarnings("unchecked")
    final var resolvedArguments = (Map<String, Object>) resolved;
    return new Resolution.Resolved(resolvedArguments);
  }

  /**
   * Resolves the document parameters of every tool call whose definition declares some; other tool
   * calls are returned unchanged.
   *
   * @throws IllegalStateException if a document parameter is invalid; the tool calls must have been
   *     validated before
   */
  public static List<ToolCall> resolveAll(
      List<ToolCall> toolCalls, List<ToolDefinition> toolDefinitions, DocumentRegistry registry) {
    final Map<String, ToolDefinition> definitionsByName = new LinkedHashMap<>();
    toolDefinitions.stream()
        .filter(definition -> !definition.documentParameters().isEmpty())
        .forEach(definition -> definitionsByName.put(definition.name(), definition));
    if (definitionsByName.isEmpty()) {
      return toolCalls;
    }

    return toolCalls.stream()
        .map(
            toolCall -> {
              final var definition = definitionsByName.get(toolCall.name());
              if (definition == null) {
                return toolCall;
              }
              return switch (resolve(toolCall, definition, registry)) {
                case Resolution.Resolved resolved -> toolCall.withArguments(resolved.arguments());
                case Resolution.Invalid invalid ->
                    throw new IllegalStateException(
                        "Tool call '%s' of tool '%s' has invalid document parameters: %s"
                            .formatted(toolCall.id(), toolCall.name(), invalid.problems()));
              };
            })
        .toList();
  }

  private sealed interface Step {
    record Property(String name) implements Step {}

    record Items() implements Step {}
  }

  /** Returns the steps of a schema pointer, or {@code null} for a pointer of an unknown shape. */
  private static @Nullable List<Step> parse(String pointer) {
    final var steps = new ArrayList<Step>();
    final var tokens = pointer.split("/", -1);
    for (int i = 1; i < tokens.length; i++) {
      switch (tokens[i]) {
        case "properties" -> {
          if (++i >= tokens.length) {
            return null;
          }
          steps.add(new Step.Property(tokens[i].replace("~1", "/").replace("~0", "~")));
        }
        case "items" -> steps.add(new Step.Items());
        default -> {
          return null;
        }
      }
    }
    return steps.isEmpty() ? null : steps;
  }

  private static Object walk(
      Object node,
      List<Step> steps,
      int index,
      String path,
      DocumentRegistry registry,
      List<String> problems) {
    if (index == steps.size()) {
      return resolveId(node, path, registry, problems);
    }

    return switch (steps.get(index)) {
      case Step.Property(var name) -> {
        if (!(node instanceof Map<?, ?> map)) {
          problems.add("Parameter '%s' must be an object.".formatted(path));
          yield node;
        }
        final var value = map.get(name);
        if (value == null) {
          yield node;
        }
        final var copy = new LinkedHashMap<Object, Object>(map);
        copy.put(
            name,
            walk(
                value,
                steps,
                index + 1,
                path.isEmpty() ? name : path + "." + name,
                registry,
                problems));
        yield copy;
      }
      case Step.Items() -> {
        if (!(node instanceof List<?> list)) {
          problems.add("Parameter '%s' must be an array.".formatted(path));
          yield node;
        }
        final var copy = new ArrayList<Object>(list.size());
        for (int i = 0; i < list.size(); i++) {
          copy.add(walk(list.get(i), steps, index + 1, path + "[" + i + "]", registry, problems));
        }
        yield copy;
      }
    };
  }

  private static Object resolveId(
      @Nullable Object value, String path, DocumentRegistry registry, List<String> problems) {
    if (!(value instanceof String id) || id.isBlank()) {
      problems.add(
          "Parameter '%s' must be the id of a known document (a non-blank string)."
              .formatted(path));
      return value == null ? "" : value;
    }
    final var entry = registry.findById(id);
    if (entry.isEmpty()) {
      problems.add("Unknown document id '%s' for parameter '%s'.".formatted(id, path));
      return id;
    }
    return entry.get().reference();
  }
}
