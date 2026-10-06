/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema.Step;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the document parameters of a tool call. The document parameters are the schema nodes
 * with the document format of the tool's input schema (see {@link DocumentParameterSchema}), found
 * at the top level, as array items and nested in objects; the arguments of a tool call mirror the
 * schema, so each such node addresses the argument values that carry a document. The model passes a
 * {@code <doc id="..." />} tag; only its {@code id} attribute is read. Every tag is replaced by the
 * Camunda document reference JSON of the registered document.
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
   * null} is left as is; a value that is not the tag of a registered document (not a string, blank,
   * not a {@code <doc/>} tag or an unknown id) is reported as a problem naming the parameter.
   */
  public static Resolution resolve(
      ToolCall toolCall, ToolDefinition toolDefinition, DocumentRegistry registry) {
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();
    final var problems = new ArrayList<String>();
    final var resolved = walkAll(arguments, toolDefinition, registry, problems, false);
    if (!problems.isEmpty()) {
      return new Resolution.Invalid(problems);
    }
    return new Resolution.Resolved(resolved);
  }

  /**
   * Replaces the tags of the document parameters of {@code arguments} that are known to the
   * registry by the document references, for reporting. Anything else is left as given: unknown
   * ids, bare ids, values of an unexpected shape, missing parameters, and the arguments of a tool
   * without document parameters. Nothing is validated, so this never fails.
   */
  public static Map<String, Object> substituteKnownReferences(
      Map<String, Object> arguments, ToolDefinition toolDefinition, DocumentRegistry registry) {
    return walkAll(arguments, toolDefinition, registry, new ArrayList<>(), true);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> walkAll(
      Map<String, Object> arguments,
      ToolDefinition toolDefinition,
      DocumentRegistry registry,
      List<String> problems,
      boolean lenient) {
    @Nullable Object resolved = arguments;
    for (final var steps :
        DocumentParameterSchema.documentParameterPaths(toolDefinition.inputSchema())) {
      resolved = walk(resolved, steps, 0, "", registry, problems, lenient);
    }
    // the root is the arguments object, which is never replaced by null
    return Objects.requireNonNull((Map<String, Object>) resolved);
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
        .filter(
            definition -> DocumentParameterSchema.hasDocumentParameters(definition.inputSchema()))
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

  private static @Nullable Object walk(
      @Nullable Object node,
      List<Step> steps,
      int index,
      String path,
      DocumentRegistry registry,
      List<String> problems,
      boolean lenient) {
    if (index == steps.size()) {
      return lenient ? substituteKnown(node, registry) : resolveId(node, path, registry, problems);
    }

    return switch (steps.get(index)) {
      case Step.Property(var name) -> {
        if (!(node instanceof Map<?, ?> map)) {
          if (!lenient) {
            problems.add("Parameter '%s' must be an object.".formatted(path));
          }
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
                problems,
                lenient));
        yield copy;
      }
      case Step.Items() -> {
        if (!(node instanceof List<?> list)) {
          if (!lenient) {
            problems.add("Parameter '%s' must be an array.".formatted(path));
          }
          yield node;
        }
        final var copy = new ArrayList<Object>(list.size());
        for (int i = 0; i < list.size(); i++) {
          copy.add(
              walk(
                  list.get(i),
                  steps,
                  index + 1,
                  path + "[" + i + "]",
                  registry,
                  problems,
                  lenient));
        }
        yield copy;
      }
    };
  }

  private static Object resolveId(
      @Nullable Object value, String path, DocumentRegistry registry, List<String> problems) {
    if (!(value instanceof String text) || text.isBlank()) {
      problems.add(
          "Parameter '%s' must be the <doc id=\"...\" /> tag of a known document (a non-blank string)."
              .formatted(path));
      return value == null ? "" : value;
    }
    final var id = DocumentReferenceXmlTag.parseId(text);
    if (id == null) {
      problems.add(
          ("Parameter '%s' must be the <doc id=\"...\" /> tag of a known document, copied from the"
                  + " conversation, not a bare id or other text: '%s'.")
              .formatted(path, text));
      return text;
    }
    final var entry = registry.findById(id);
    if (entry.isEmpty()) {
      problems.add("Unknown document id '%s' for parameter '%s'.".formatted(id, path));
      return text;
    }
    return entry.get().reference();
  }

  private static @Nullable Object substituteKnown(
      @Nullable Object value, DocumentRegistry registry) {
    if (value instanceof String text) {
      final var id = DocumentReferenceXmlTag.parseId(text);
      if (id != null) {
        return registry.findById(id).<Object>map(DocumentRegistryEntry::reference).orElse(value);
      }
    }
    return value;
  }
}
