/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema.Step;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the document parameters of a tool call. The document parameters are the schema nodes
 * with the document format of the tool's input schema and its union parameters (see {@link
 * DocumentParameterSchema}), found at the top level, as array items and nested in objects; the
 * arguments of a tool call mirror the schema, so each such node addresses the argument values that
 * carry a document.
 *
 * <p>For a document parameter the model passes the id of a known document, exactly as the {@code
 * id} attribute of its {@code <doc />} tag, or the tag itself: a value that is exactly one tag,
 * surrounded by whitespace at most, stands for the document of its {@code id} attribute.
 *
 * <p>For a union parameter only a value that is exactly one tag stands for a document. Any other
 * value is left as it is: a plain string, also one that equals the id of a known document, text
 * that holds a tag, and a value that is not a string.
 *
 * <p>Every document is replaced by the Camunda document reference JSON of the registered document,
 * or, for a tool that looks the documents up itself, by its plain id.
 */
public final class DocumentParameterResolver {

  private DocumentParameterResolver() {}

  /** Outcome of resolving the document parameters of one tool call. */
  public sealed interface Resolution {

    /**
     * All document ids are known; {@code arguments} carry the document references or, from {@link
     * #resolveIds}, the plain ids.
     */
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
   * null} is left as is. For a document parameter, a value that is neither exactly the id of a
   * registered document nor exactly one tag with such an id (not a string, text around a tag, more
   * than one tag, an unknown id) is reported as a problem naming the parameter. For a union
   * parameter, only a tag with an unknown id is reported.
   */
  public static Resolution resolve(
      ToolCall toolCall, ToolDefinition toolDefinition, DocumentRegistry registry) {
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();
    return walkValidating(arguments, toolDefinition, registry, DocumentRegistryEntry::reference);
  }

  /**
   * Validates the document parameters of {@code toolCall} like {@link #resolve}, but replaces each
   * valid value by the plain id of its document: a bare id stays as it is and a tag becomes the id
   * of the tag. A union parameter that holds text stays that text.
   */
  public static Resolution resolveIds(
      ToolCall toolCall, ToolDefinition toolDefinition, DocumentRegistry registry) {
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();
    return walkValidating(arguments, toolDefinition, registry, DocumentRegistryEntry::id);
  }

  private static Resolution walkValidating(
      Map<String, Object> arguments,
      ToolDefinition toolDefinition,
      DocumentRegistry registry,
      Function<DocumentRegistryEntry, Object> replacement) {
    final var problems = new ArrayList<String>();
    final var resolved = walkAll(arguments, toolDefinition, registry, replacement, problems);
    if (!problems.isEmpty()) {
      return new Resolution.Invalid(problems);
    }
    return new Resolution.Resolved(resolved);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> walkAll(
      Map<String, Object> arguments,
      ToolDefinition toolDefinition,
      DocumentRegistry registry,
      Function<DocumentRegistryEntry, Object> replacement,
      List<String> problems) {
    @Nullable Object resolved = arguments;
    for (final var parameter :
        DocumentParameterSchema.documentParameters(toolDefinition.inputSchema())) {
      resolved = walk(resolved, parameter, 0, "", registry, replacement, problems);
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
      DocumentParameter parameter,
      int index,
      String path,
      DocumentRegistry registry,
      Function<DocumentRegistryEntry, Object> replacement,
      List<String> problems) {
    final var steps = parameter.path();
    if (index == steps.size()) {
      return parameter.union()
          ? resolveUnion(node, path, registry, replacement, problems)
          : resolveId(node, path, registry, replacement, problems);
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
                parameter,
                index + 1,
                path.isEmpty() ? name : path + "." + name,
                registry,
                replacement,
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
          copy.add(
              walk(
                  list.get(i),
                  parameter,
                  index + 1,
                  path + "[" + i + "]",
                  registry,
                  replacement,
                  problems));
        }
        yield copy;
      }
    };
  }

  /**
   * A union parameter: only a value that is exactly one tag stands for a document, every other
   * value is returned as it is.
   */
  private static @Nullable Object resolveUnion(
      @Nullable Object value,
      String path,
      DocumentRegistry registry,
      Function<DocumentRegistryEntry, Object> replacement,
      List<String> problems) {
    if (!(value instanceof String text)) {
      return value;
    }
    final var tagId = DocumentTag.idOfSingleTag(text);
    if (tagId == null) {
      return text;
    }
    final var entry = registry.findById(tagId);
    if (entry.isEmpty()) {
      problems.add(unknownUnionTagProblem(path, tagId));
      return text;
    }
    return replacement.apply(entry.get());
  }

  private static Object resolveId(
      @Nullable Object value,
      String path,
      DocumentRegistry registry,
      Function<DocumentRegistryEntry, Object> replacement,
      List<String> problems) {
    if (!(value instanceof String text)) {
      problems.add(
          ("Parameter '%s' must be the exact id of a known document (the id attribute of its"
                  + " <doc /> tag) or that tag itself, a string.")
              .formatted(path));
      return value == null ? "" : value;
    }
    final var id = idOf(text);
    final var entry = registry.findById(id);
    if (entry.isEmpty()) {
      problems.add(unknownIdProblem(path, id));
      return id;
    }
    return replacement.apply(entry.get());
  }

  private static String unknownIdProblem(String path, String id) {
    return ("Parameter '%s' must be the exact id of a known document (the id attribute of its"
            + " <doc /> tag) or that tag itself, got '%s'.")
        .formatted(path, id);
  }

  private static String unknownUnionTagProblem(String path, String id) {
    return ("Parameter '%s' holds a <doc /> tag with the id '%s'. That is not a known document."
            + " Pass the tag of a document exactly as you read it, or pass other text.")
        .formatted(path, id);
  }

  /** The id a value stands for: the id attribute of a value that is one tag, else the value. */
  private static String idOf(String value) {
    final var tagId = DocumentTag.idOfSingleTag(value);
    return tagId != null ? tagId : value;
  }
}
