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
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the {@link DocumentParameter document parameters} of a tool call. The value of a
 * document parameter must be the handle of a document the agent knows:
 *
 * <pre>{@code
 * {"type": "camunda.agenticai.document", "id": "doc_0123456789ab"}
 * }</pre>
 *
 * A string that holds such an object as JSON is a handle too. Other fields of the object are
 * ignored. Every handle is replaced by the Camunda document reference JSON of the document for a
 * tool of the process, and by the {@link AgentDocumentId} of the document for a runtime tool. Any
 * other value is invalid, and the call gets one problem per invalid value, which tells the model
 * what to pass instead.
 */
public class DocumentParameterResolver {

  private static final String HANDLE_EXAMPLE =
      "a JSON object such as {\"type\":\"%s\",\"id\":\"doc_…\"}."
          .formatted(AgentDocumentHandle.TYPE);

  // a mapper without the modules of the runtime: it reads every value as plain data, so neither a
  // value the model sent nor a stored reference turns into a document or runs an intrinsic function
  private static final ObjectMapper DATA_MAPPER = new ObjectMapper();

  private static final ObjectReader JSON_READER =
      DATA_MAPPER.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private final ObjectMapper referenceMapper;

  public DocumentParameterResolver(ObjectMapper objectMapper) {
    this.referenceMapper = objectMapper;
  }

  /** Outcome of resolving the document parameters of one tool call. */
  public sealed interface Resolution {

    /**
     * All document parameters are valid; {@code arguments} carry the document references or, for a
     * runtime tool, the {@link AgentDocumentId}s.
     */
    record Resolved(Map<String, Object> arguments) implements Resolution {}

    /** At least one document parameter is invalid; one problem per invalid value. */
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
   * Resolves the document parameters of a call of the tool {@code definition}. The arguments stay
   * as they are when the agent instance does not track its documents or the tool has no document
   * parameters.
   *
   * <p>The admission of a call, the call that goes to the process and the execution of a runtime
   * call all use this method, so they agree on whether a call is valid.
   */
  public Resolution resolve(
      AgentContext agentContext, ToolDefinition definition, ToolCall toolCall) {
    final var parameters = definition.documentParameters();
    if (parameters.isEmpty()) {
      return new Resolution.Resolved(toolCall.arguments());
    }
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Untracked ignored -> new Resolution.Resolved(toolCall.arguments());
      case DocumentHandling.Tracked tracked ->
          new CallResolution(tracked.documents(), replacement(definition.kind()))
              .resolve(parameters, toolCall);
    };
  }

  private Function<AgentDocument, Object> replacement(ToolKind kind) {
    return switch (kind) {
      case PROCESS -> this::referenceJson;
      case RUNTIME -> AgentDocument::id;
    };
  }

  /** The document reference in the JSON format it is persisted in. */
  private Object referenceJson(AgentDocument document) {
    return DATA_MAPPER.convertValue(
        referenceMapper.valueToTree(document).path("reference"),
        new TypeReference<Map<String, Object>>() {});
  }

  /**
   * The resolution of one call: the documents it can name, what replaces a document, and the
   * problems found. Every method takes the value and its path in the arguments, such as {@code
   * message.files[1]}, which the problems name.
   */
  private static final class CallResolution {

    private final AgentDocuments documents;
    private final Function<AgentDocument, Object> replacement;
    private final List<String> problems = new ArrayList<>();

    private CallResolution(AgentDocuments documents, Function<AgentDocument, Object> replacement) {
      this.documents = documents;
      this.replacement = replacement;
    }

    // the arguments are a JSON object, and a resolved object is never null and keeps its keys
    @SuppressWarnings("unchecked")
    Resolution resolve(List<DocumentParameter> parameters, ToolCall toolCall) {
      Map<String, Object> arguments = toolCall.arguments();
      for (final var parameter : parameters) {
        arguments =
            (Map<String, Object>) Objects.requireNonNull(resolve(arguments, "", parameter.path()));
      }
      return problems.isEmpty()
          ? new Resolution.Resolved(arguments)
          : new Resolution.Invalid(problems);
    }

    /** Resolves the document at the end of {@code steps}, which lead from {@code value} to it. */
    private @Nullable Object resolve(@Nullable Object value, String path, List<Step> steps) {
      if (steps.isEmpty()) {
        return resolveDocument(value, path);
      }
      final var rest = steps.subList(1, steps.size());
      return switch (steps.getFirst()) {
        case Step.Property(var name) -> resolveProperty(value, path, name, rest);
        case Step.Items() -> resolveItems(value, path, rest);
      };
    }

    /** A missing or {@code null} property stays as it is. */
    private @Nullable Object resolveProperty(
        @Nullable Object value, String path, String name, List<Step> rest) {
      if (!(value instanceof Map<?, ?> object)) {
        problems.add("Parameter '%s' must be an object.".formatted(path));
        return value;
      }
      final var property = object.get(name);
      if (property == null) {
        return value;
      }
      final var copy = new LinkedHashMap<Object, Object>(object);
      copy.put(name, resolve(property, path.isEmpty() ? name : path + "." + name, rest));
      return copy;
    }

    private @Nullable Object resolveItems(@Nullable Object value, String path, List<Step> rest) {
      if (!(value instanceof List<?> list)) {
        problems.add("Parameter '%s' must be an array.".formatted(path));
        return value;
      }
      final var copy = new ArrayList<@Nullable Object>(list.size());
      for (int i = 0; i < list.size(); i++) {
        copy.add(resolve(list.get(i), path + "[" + i + "]", rest));
      }
      return copy;
    }

    private @Nullable Object resolveDocument(@Nullable Object value, String path) {
      if (documents.all().isEmpty()) {
        problems.add(
            ("Parameter '%s' takes a document, but the conversation has no documents."
                    + " Do not pass a document.")
                .formatted(path));
        return value;
      }
      final var id = handleId(value);
      if (id == null) {
        problems.add(
            "Parameter '%s' must be the handle of a document from the conversation: %s"
                .formatted(path, HANDLE_EXAMPLE));
        return value;
      }
      final var document = documents.find(AgentDocumentId.of(id));
      if (document.isEmpty()) {
        problems.add(
            ("Parameter '%s' has the id '%s', but the conversation has no document with this id."
                    + " Pass the handle of a document from the conversation: %s")
                .formatted(path, id, HANDLE_EXAMPLE));
        return value;
      }
      return replacement.apply(document.get());
    }
  }

  /**
   * The id of the handle a value stands for: the object itself, or the object a string holds as
   * JSON, when its {@code type} is the handle type; {@code null} for every other value and for a
   * handle without an id.
   */
  private static @Nullable String handleId(@Nullable Object value) {
    final var object = value instanceof String text ? parseObject(text) : value;
    return object instanceof Map<?, ?> handle
            && AgentDocumentHandle.TYPE.equals(handle.get(AgentDocumentHandle.FIELD_TYPE))
            && handle.get(AgentDocumentHandle.FIELD_ID) instanceof String id
            && !id.isBlank()
        ? id
        : null;
  }

  /** The JSON object a string holds as a whole, or {@code null} for any other string. */
  private static @Nullable Map<?, ?> parseObject(String text) {
    if (!text.stripLeading().startsWith("{")) {
      return null;
    }
    try {
      final JsonNode node = JSON_READER.readTree(text);
      return node != null && node.isObject()
          ? DATA_MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {})
          : null;
    } catch (JsonProcessingException | IllegalArgumentException e) {
      return null;
    }
  }
}
