/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
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
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the document parameters of a tool call. The document parameters are the schema nodes
 * with the document format of the tool's input schema and its union parameters (see {@link
 * DocumentParameterSchema}), found at the top level, as array items and nested in objects; the
 * arguments of a tool call mirror the schema, so each such node addresses the argument values that
 * carry a document.
 *
 * <p>For a document parameter the model passes the {@code <doc />} tag of a known document: a value
 * that is exactly one tag, surrounded by whitespace at most, stands for the document of its {@code
 * id} attribute. Any other value is invalid, also a string that equals the id of a known document.
 *
 * <p>For a union parameter only a value that is exactly one tag stands for a document. Any other
 * value is left as it is: a plain string, also one that equals the id of a known document, text
 * that holds a tag, and a value that is not a string.
 *
 * <p>Every document is replaced by the Camunda document reference JSON of the registered document
 * ({@link #resolve}), or, for a runtime tool that looks the documents up itself, by its {@link
 * DocumentId} ({@link #resolveIds}). The result depends on the call, the tool definition and the
 * document registry only, so the agent computes the same outcome wherever it needs it: when it
 * decides who answers a call, when it hands a call to the process and when it runs a runtime tool.
 * An agent instance without the {@link AgentFeatures#DOCUMENTS documents feature} has no document
 * parameters ({@link #resolveProcessCall}, {@link #resolveRuntimeCall}).
 */
public final class DocumentParameterResolver {

  private DocumentParameterResolver() {}

  /** Outcome of resolving the document parameters of one tool call. */
  public sealed interface Resolution {

    /**
     * All document ids are known; {@code arguments} carry the document references or, from {@link
     * #resolveIds}, the {@link DocumentId}s.
     */
    record Resolved(Map<String, Object> arguments) implements Resolution {}

    /** At least one document parameter is invalid; one message per invalid value. */
    record Invalid(List<String> problems) implements Resolution {
      public Invalid {
        problems = List.copyOf(problems);
      }

      /** The problems as one text, the error the model reads. */
      public String message() {
        return String.join(" ", problems);
      }
    }
  }

  /**
   * Resolves the document parameters of a call of a tool of the process, a tool that is not a
   * runtime tool, with the tool definition of the agent context: the documents become the Camunda
   * document references. The call is resolved as it is, when the agent instance has no documents
   * feature or the tool has no document parameters.
   */
  public static Resolution resolveProcessCall(AgentContext agentContext, ToolCall toolCall) {
    if (!agentContext.supports(AgentFeatures.DOCUMENTS)) {
      return unchanged(toolCall);
    }
    return agentContext.toolDefinitions().stream()
        .filter(definition -> definition.name().equals(toolCall.name()))
        .filter(
            definition -> DocumentParameterSchema.hasDocumentParameters(definition.inputSchema()))
        .findFirst()
        .map(definition -> resolve(toolCall, definition, agentContext.documentRegistry()))
        .orElseGet(() -> unchanged(toolCall));
  }

  /**
   * Resolves the document parameters of a call of a runtime tool: the documents become their {@link
   * DocumentId}s. The call is resolved as it is, when the agent instance has no documents feature
   * or the tool has no document parameters.
   */
  public static Resolution resolveRuntimeCall(
      AgentContext agentContext, ToolCall toolCall, ToolDefinition definition) {
    if (!agentContext.supports(AgentFeatures.DOCUMENTS)
        || !DocumentParameterSchema.hasDocumentParameters(definition.inputSchema())) {
      return unchanged(toolCall);
    }
    return resolveIds(toolCall, definition, agentContext.documentRegistry());
  }

  private static Resolution unchanged(ToolCall toolCall) {
    return new Resolution.Resolved(toolCall.arguments() == null ? Map.of() : toolCall.arguments());
  }

  /**
   * Resolves the document parameters of {@code toolCall}. A parameter that is missing or {@code
   * null} is left as is. For a document parameter, a value that is not exactly one tag with the id
   * of a registered document (not a string, a bare id, text around a tag, more than one tag, an
   * unknown id) is reported as a problem naming the parameter. For a union parameter, only a tag
   * with an unknown id is reported.
   */
  public static Resolution resolve(
      ToolCall toolCall, ToolDefinition toolDefinition, DocumentRegistry registry) {
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();
    return walkValidating(arguments, toolDefinition, registry, DocumentRegistryEntry::reference);
  }

  /**
   * Validates the document parameters of {@code toolCall} like {@link #resolve}, but replaces each
   * valid value by the {@link DocumentId} of its document: a tag becomes the id of the tag. A union
   * parameter that holds text stays that text.
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
          ("Parameter '%s' must be the <doc /> tag of a known document, exactly as you read it, a"
                  + " string.")
              .formatted(path));
      return value == null ? "" : value;
    }
    final var tagId = DocumentTag.idOfSingleTag(text);
    final var entry =
        tagId == null ? Optional.<DocumentRegistryEntry>empty() : registry.findById(tagId);
    if (entry.isEmpty()) {
      problems.add(invalidDocumentProblem(path, text));
      return text;
    }
    return replacement.apply(entry.get());
  }

  private static String invalidDocumentProblem(String path, String value) {
    return ("Parameter '%s' must be the <doc /> tag of a known document, exactly as you read it,"
            + " got '%s'.")
        .formatted(path, value);
  }

  private static String unknownUnionTagProblem(String path, DocumentId id) {
    return ("Parameter '%s' holds a <doc /> tag with the id '%s'. That is not a known document."
            + " Pass the tag of a document exactly as you read it, or pass other text.")
        .formatted(path, id);
  }
}
