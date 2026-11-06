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
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
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
 * Resolves the document parameters of a tool call, the {@link DocumentParameter}s of the input
 * schema of the tool; the arguments of a tool call mirror the schema, so each parameter addresses
 * the argument values that carry a document.
 *
 * <p>A value stands for a document when it is a document handle: an object with the {@code type}
 * {@value DocumentHandle#TYPE} and the {@code id} of a known document. Other fields of the object
 * are ignored; the {@code type} is required.
 *
 * <p>For a {@link DocumentParameter.Document} the value is a handle object, or a string that holds
 * such an object as JSON. Any other value is invalid: an object without the {@code type}, an
 * unknown id, a bare id, other text.
 *
 * <p>For a {@link DocumentParameter.TextOrDocument} a string is always text, also one that equals
 * the id of a known document or holds a handle. An object with the handle {@code type} is a
 * document, and invalid when its id is unknown. Any other value, such as an object without the
 * {@code type} for another alternative of the union, is left as it is.
 *
 * <p>Every document is replaced by the Camunda document reference JSON of the registered document
 * ({@link #resolveProcessCall}), or, for a runtime tool that looks the documents up itself, by its
 * {@link AgentDocumentId} ({@link #resolveRuntimeCall}). {@link #check} validates a call of a tool
 * of either kind. The result depends on the call, the tool definition and the agent documents only,
 * so the agent computes the same outcome wherever it needs it: when it admits a call, when it hands
 * a call to the process and when it runs a runtime tool. An {@link DocumentHandling.Untracked
 * untracked} agent instance has no document parameters.
 */
public class DocumentParameterResolver {

  private static final String EXPECTED_HANDLE =
      "a document handle such as {\"type\":\"%s\",\"id\":\"doc_…\"} of a known document."
          .formatted(DocumentHandle.TYPE);

  private final ObjectMapper referenceMapper = ConnectorsObjectMapperSupplier.getCopy();

  private final ObjectReader jsonReader =
      referenceMapper
          .readerFor(JsonNode.class)
          .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

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
    return referenceMapper.convertValue(
        referenceMapper.valueToTree(document).path("reference"),
        new TypeReference<Map<String, Object>>() {});
  }

  private static Resolution unchanged(ToolCall toolCall) {
    return new Resolution.Resolved(toolCall.arguments());
  }

  /**
   * Resolves the document parameters of {@code toolCall}. A parameter that is missing or {@code
   * null} is left as is. For a document parameter, a value that is not a handle of a registered
   * document is reported as a problem naming the parameter. For a text-or-document parameter, only
   * a handle object with an unknown id is reported. Each valid document is replaced by what {@code
   * replacement} returns for it.
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
        case DocumentParameter.TextOrDocument ignored ->
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
   * A union parameter: only an object with the handle type stands for a document, every other value
   * is returned as it is.
   */
  private static @Nullable Object resolveUnion(
      @Nullable Object value,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    if (!(value instanceof Map<?, ?> object) || !DocumentHandle.TYPE.equals(object.get("type"))) {
      return value;
    }
    return resolveHandle(object, path, documents, replacement, problems);
  }

  private @Nullable Object resolveDocument(
      @Nullable Object value,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    final var object = value instanceof String text ? parseObject(text) : value;
    if (!(object instanceof Map<?, ?> map)) {
      problems.add(invalidProblem(path));
      return value;
    }
    return resolveHandle(map, path, documents, replacement, problems);
  }

  private static @Nullable Object resolveHandle(
      Map<?, ?> handle,
      String path,
      AgentDocuments documents,
      Function<AgentDocument, Object> replacement,
      List<String> problems) {
    final var id = handle.get("id");
    final var document =
        DocumentHandle.TYPE.equals(handle.get("type"))
                && id instanceof String text
                && !text.isBlank()
            ? documents.find(AgentDocumentId.of(text))
            : Optional.<AgentDocument>empty();
    if (document.isEmpty()) {
      problems.add(invalidProblem(path));
      return handle;
    }
    return replacement.apply(document.get());
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

  private static String invalidProblem(String path) {
    return "Parameter '%s' must be %s".formatted(path, EXPECTED_HANDLE);
  }
}
