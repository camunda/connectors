/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema.Step;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
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
 * AgentDocumentId} ({@link #resolveIds}). The result depends on the call, the tool definition and
 * the agent documents only, so the agent computes the same outcome wherever it needs it: when it
 * decides who answers a call, when it hands a call to the process and when it runs a runtime tool.
 * An {@link DocumentHandling.Untracked untracked} agent instance has no document parameters ({@link
 * #resolveProcessCall}, {@link #resolveRuntimeCall}).
 */
public final class DocumentParameterResolver {

  private static final ObjectMapper REFERENCE_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  private DocumentParameterResolver() {}

  /** Outcome of resolving the document parameters of one tool call. */
  public sealed interface Resolution {

    /**
     * All document ids are known; {@code arguments} carry the document references or, from {@link
     * #resolveIds}, the {@link AgentDocumentId}s.
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
   * document references. The call is resolved as it is, when the agent instance does not track its
   * documents or the tool has no document parameters.
   */
  public static Resolution resolveProcessCall(AgentContext agentContext, ToolCall toolCall) {
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Untracked ignored -> unchanged(toolCall);
      case DocumentHandling.Tracked tracked ->
          agentContext.toolDefinitions().stream()
              .filter(definition -> definition.name().equals(toolCall.name()))
              .filter(definition ->
                  DocumentParameterSchema.hasDocumentParameters(definition.inputSchema()))
              .findFirst()
              .map(definition -> resolve(toolCall, definition, tracked.documents()))
              .orElseGet(() -> unchanged(toolCall));
    };
  }

  /**
   * Checks the document parameters of a call of a tool of either kind against the documents the
   * agent knows. The call is valid as it is when the agent instance does not track its documents or
   * the tool has no document parameter.
   */
  public static Resolution check(
      AgentContext agentContext, ToolCall toolCall, ToolDefinition definition) {
    if (!DocumentParameterSchema.hasDocumentParameters(definition.inputSchema())) {
      return unchanged(toolCall);
    }
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Untracked ignored -> unchanged(toolCall);
      case DocumentHandling.Tracked tracked ->
          resolveIds(toolCall, definition, tracked.documents());
    };
  }

  /**
   * Resolves the document parameters of a call of a runtime tool: the documents become their {@link
   * AgentDocumentId}s. The call is resolved as it is, when the agent instance does not track its
   * documents or the tool has no document parameters.
   */
  public static Resolution resolveRuntimeCall(
      AgentContext agentContext, ToolCall toolCall, ToolDefinition definition) {
    return check(agentContext, toolCall, definition);
  }

  /** The document reference in the JSON format it is persisted in. */
  private static Object referenceJson(AgentDocument document) {
    return REFERENCE_MAPPER.convertValue(
        REFERENCE_MAPPER.valueToTree(document).path("reference"),
        new TypeReference<Map<String, Object>>() {});
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
      ToolCall toolCall, ToolDefinition toolDefinition, AgentDocuments documents) {
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();
    return walkValidating(
        arguments, toolDefinition, documents, DocumentParameterResolver::referenceJson);
  }

  /**
   * Validates the document parameters of {@code toolCall} like {@link #resolve}, but replaces each
   * valid value by the {@link AgentDocumentId} of its document: a tag becomes the id of the tag. A
   * union parameter that holds text stays that text.
   */
  public static Resolution resolveIds(
      ToolCall toolCall, ToolDefinition toolDefinition, AgentDocuments documents) {
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();
    return walkValidating(arguments, toolDefinition, documents, AgentDocument::id);
  }

  private static Resolution walkValidating(
      Map<String, Object> arguments,
      ToolDefinition toolDefinition,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement) {
    final var problems = new ArrayList<String>();
    final var resolved = walkAll(arguments, toolDefinition, documents, replacement, problems);
    if (!problems.isEmpty()) {
      return new Resolution.Invalid(problems);
    }
    return new Resolution.Resolved(resolved);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> walkAll(
      Map<String, Object> arguments,
      ToolDefinition toolDefinition,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    @Nullable Object resolved = arguments;
    for (final var parameter :
        DocumentParameterSchema.documentParameters(toolDefinition.inputSchema())) {
      resolved = walk(resolved, parameter, 0, "", documents, replacement, problems);
    }
    // the root is the arguments object, which is never replaced by null
    return Objects.requireNonNull((Map<String, Object>) resolved);
  }

  private static @Nullable Object walk(
      @Nullable Object node,
      DocumentParameter parameter,
      int index,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    final var steps = parameter.path();
    if (index == steps.size()) {
      return parameter.union()
          ? resolveUnion(node, path, documents, replacement, problems)
          : resolveId(node, path, documents, replacement, problems);
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
                documents,
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
                  documents,
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
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    if (!(value instanceof String text)) {
      return value;
    }
    final var tagId = DocumentTag.idOfSingleTag(text);
    if (tagId == null) {
      return text;
    }
    final var entry = documents.find(tagId);
    if (entry.isEmpty()) {
      problems.add(unknownUnionTagProblem(path, tagId));
      return text;
    }
    return replacement.apply(entry.get());
  }

  private static Object resolveId(
      @Nullable Object value,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    if (!(value instanceof String text)) {
      problems.add(
          ("Parameter '%s' must be the <doc /> tag of a known document, exactly as you read it, a"
                  + " string.")
              .formatted(path));
      return value == null ? "" : value;
    }
    final var tagId = DocumentTag.idOfSingleTag(text);
    final var entry = tagId == null ? Optional.<AgentDocument>empty() : documents.find(tagId);
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

  private static String unknownUnionTagProblem(String path, AgentDocumentId id) {
    return ("Parameter '%s' holds a <doc /> tag with the id '%s'. That is not a known document."
            + " Pass the tag of a document exactly as you read it, or pass other text.")
        .formatted(path, id);
  }
}
