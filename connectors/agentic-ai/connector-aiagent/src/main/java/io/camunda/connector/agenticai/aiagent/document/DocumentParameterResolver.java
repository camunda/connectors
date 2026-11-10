/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
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
 * Resolves the document parameters of a tool call, the {@link DocumentParameter}s of the input
 * schema of the tool; the arguments of a tool call mirror the schema, so each parameter addresses
 * the argument values that carry a document.
 *
 * <p>A value is a document handle when it is an object with the {@code type} {@value
 * AgentDocumentHandle#TYPE}, or a string that is exactly such an object as JSON (surrounding
 * whitespace is ignored). Other fields of the object are ignored; the {@code type} is required. A
 * string that is the id of a document is no handle.
 *
 * <p>For a {@link DocumentParameter.Document} the value must be a handle of a known document. Any
 * other value is invalid: a handle with an unknown id, an object without the {@code type}, text.
 *
 * <p>For a {@link DocumentParameter.Union} the value is scanned: every handle at or below it, also
 * inside objects and arrays, is a document, and invalid when its id is unknown. Everything else is
 * left as it is: text, numbers, booleans and {@code null}, and the rest of an object or an array
 * around its handles.
 *
 * <p>The problem of an invalid value tells the model what to do: that the agent knows no documents
 * and none is to be passed, that the id of the handle is unknown, or what a handle looks like.
 *
 * <p>Every document is replaced by the Camunda document reference JSON of the registered document
 * ({@link #resolveProcessCall}), or, for a runtime tool that looks the documents up itself, by its
 * {@link AgentDocumentId} ({@link #resolveRuntimeCall}). {@link #check} validates a call of a tool
 * of either kind. The result depends on the call, the tool definition and the agent documents only.
 * An {@link DocumentHandling.Untracked untracked} agent instance has no document parameters.
 */
public class DocumentParameterResolver {

  private static final String HANDLE_EXAMPLE =
      "a JSON object such as {\"type\":\"%s\",\"id\":\"doc_…\"}."
          .formatted(AgentDocumentHandle.TYPE);

  // a mapper without the modules of the runtime: it reads every value as plain data
  private static final ObjectMapper DATA_MAPPER = new ObjectMapper();

  private final ObjectMapper referenceMapper;
  private final ObjectReader jsonReader;

  public DocumentParameterResolver(ObjectMapper objectMapper) {
    this.referenceMapper = objectMapper;
    this.jsonReader =
        objectMapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  /** Outcome of resolving the document parameters of one tool call. */
  public sealed interface Resolution {

    /**
     * All document ids are known; {@code arguments} carry the document references or, for a runtime
     * call, the {@link AgentDocumentId}s.
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
  public Resolution resolveProcessCall(AgentContext agentContext, ToolCall toolCall) {
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Untracked ignored -> unchanged(toolCall);
      case DocumentHandling.Tracked tracked ->
          agentContext.toolDefinitions().stream()
              .filter(definition -> definition.name().equals(toolCall.name()))
              .filter(definition -> !definition.documentParameters().isEmpty())
              .findFirst()
              .map(definition ->
                  resolve(toolCall, definition, tracked.documents(), this::referenceJson))
              .orElseGet(() -> unchanged(toolCall));
    };
  }

  /**
   * Resolves the document parameters of a call of a runtime tool: the documents become their {@link
   * AgentDocumentId}s. The call is resolved as it is, when the agent instance does not track its
   * documents or the tool has no document parameters.
   */
  public Resolution resolveRuntimeCall(
      AgentContext agentContext, ToolCall toolCall, ToolDefinition definition) {
    if (definition.documentParameters().isEmpty()) {
      return unchanged(toolCall);
    }
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Untracked ignored -> unchanged(toolCall);
      case DocumentHandling.Tracked tracked ->
          resolve(toolCall, definition, tracked.documents(), AgentDocument::id);
    };
  }

  /**
   * Checks the document parameters of a call of a tool of either kind against the documents the
   * agent knows, for the admission of the call. The outcome is the one of {@link
   * #resolveRuntimeCall}: the call is valid as it is when the agent instance does not track its
   * documents or the tool has no document parameter.
   */
  public Resolution check(AgentContext agentContext, ToolCall toolCall, ToolDefinition definition) {
    return resolveRuntimeCall(agentContext, toolCall, definition);
  }

  /** The document reference in the JSON format it is persisted in. */
  private Object referenceJson(AgentDocument document) {
    return DATA_MAPPER.convertValue(
        referenceMapper.valueToTree(document).path("reference"),
        new TypeReference<Map<String, Object>>() {});
  }

  private static Resolution unchanged(ToolCall toolCall) {
    return new Resolution.Resolved(toolCall.arguments());
  }

  /**
   * Resolves the document parameters of {@code toolCall}. A parameter that is missing or {@code
   * null} is left as is. For a document parameter, a value that is not a handle of a registered
   * document is reported as a problem naming the value. For a union parameter, only a handle with
   * an unknown id is reported. Each valid document is replaced by what {@code replacement} returns
   * for it.
   */
  private Resolution resolve(
      ToolCall toolCall,
      ToolDefinition toolDefinition,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement) {
    final var problems = new ArrayList<String>();
    final var resolved = walkAll(toolCall, toolDefinition, documents, replacement, problems);
    return problems.isEmpty()
        ? new Resolution.Resolved(resolved)
        : new Resolution.Invalid(problems);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> walkAll(
      ToolCall toolCall,
      ToolDefinition toolDefinition,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    @Nullable Object resolved = toolCall.arguments();
    for (final var parameter : toolDefinition.documentParameters()) {
      resolved = walk(resolved, parameter, 0, "", documents, replacement, problems);
    }
    // the root is the arguments object, which is never replaced by null
    return Objects.requireNonNull((Map<String, Object>) resolved);
  }

  private @Nullable Object walk(
      @Nullable Object node,
      DocumentParameter parameter,
      int index,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    final var steps = parameter.path();
    if (index == steps.size()) {
      return switch (parameter) {
        case DocumentParameter.Document ignored ->
            resolveDocument(node, path, documents, replacement, problems);
        case DocumentParameter.Union ignored ->
            resolveUnion(node, path, documents, replacement, problems);
      };
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
   * A union parameter: every handle at or below the value is resolved, every other value is
   * returned as it is.
   */
  private @Nullable Object resolveUnion(
      @Nullable Object value,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    final var handle = asHandle(value);
    if (handle != null) {
      return resolveHandle(handle, path, documents, replacement, problems, true);
    }
    if (value instanceof Map<?, ?> object) {
      final var copy = new LinkedHashMap<Object, Object>();
      object.forEach((key, child) ->
          copy.put(
              key,
              resolveUnion(
                  child,
                  path.isEmpty() ? String.valueOf(key) : path + "." + key,
                  documents,
                  replacement,
                  problems)));
      return copy;
    }
    if (value instanceof List<?> list) {
      final var copy = new ArrayList<@Nullable Object>(list.size());
      for (int i = 0; i < list.size(); i++) {
        copy.add(resolveUnion(list.get(i), path + "[" + i + "]", documents, replacement, problems));
      }
      return copy;
    }
    return value;
  }

  private @Nullable Object resolveDocument(
      @Nullable Object value,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    final var handle = asHandle(value);
    if (handle == null) {
      problems.add(
          documents.all().isEmpty() ? noDocumentsProblem(path) : mustBeHandleProblem(path));
      return value;
    }
    return resolveHandle(handle, path, documents, replacement, problems, false);
  }

  private static @Nullable Object resolveHandle(
      Map<?, ?> handle,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems,
      boolean inUnion) {
    final var id = handle.get("id") instanceof String text && !text.isBlank() ? text : null;
    final var document =
        id != null ? documents.find(AgentDocumentId.of(id)) : Optional.<AgentDocument>empty();
    if (document.isEmpty()) {
      problems.add(unknownHandleProblem(path, documents, id, inUnion));
      return handle;
    }
    return replacement.apply(document.get());
  }

  /**
   * The handle a value stands for: the object itself, or the object a string holds as JSON, when
   * its {@code type} is the handle type; {@code null} for every other value.
   */
  private @Nullable Map<?, ?> asHandle(@Nullable Object value) {
    final var object = value instanceof String text ? parseObject(text) : value;
    return object instanceof Map<?, ?> map && AgentDocumentHandle.TYPE.equals(map.get("type"))
        ? map
        : null;
  }

  /** The JSON object a string holds as a whole, or {@code null} for any other string. */
  private @Nullable Map<?, ?> parseObject(String text) {
    if (!text.stripLeading().startsWith("{")) {
      return null;
    }
    try {
      final JsonNode node = jsonReader.readTree(text);
      return node != null && node.isObject()
          ? referenceMapper.convertValue(node, new TypeReference<Map<String, Object>>() {})
          : null;
    } catch (JsonProcessingException | IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * The problem of a handle whose document is not known: the agent knows no documents, or the id of
   * the handle is not known. A handle in a union is reached only when the value holds one.
   */
  private static String unknownHandleProblem(
      String path, AgentDocuments documents, @Nullable String id, boolean inUnion) {
    if (documents.all().isEmpty()) {
      return inUnion
          ? "Parameter '%s' holds a document handle, but the conversation has no documents. Do not pass a document handle."
              .formatted(path)
          : noDocumentsProblem(path);
    }
    return unknownIdProblem(path, id);
  }

  private static String noDocumentsProblem(String path) {
    return "Parameter '%s' takes a document, but the conversation has no documents. Do not pass a document."
        .formatted(path);
  }

  /** A handle of an unknown id, or a handle without a usable id. */
  private static String unknownIdProblem(String path, @Nullable String id) {
    if (id == null) {
      return mustBeHandleProblem(path);
    }
    return ("Parameter '%s' has the id '%s', but the conversation has no document with this id."
            + " Pass the handle of a document from the conversation: %s")
        .formatted(path, id, HANDLE_EXAMPLE);
  }

  private static String mustBeHandleProblem(String path) {
    return "Parameter '%s' must be the handle of a document from the conversation: %s"
        .formatted(path, HANDLE_EXAMPLE);
  }
}
