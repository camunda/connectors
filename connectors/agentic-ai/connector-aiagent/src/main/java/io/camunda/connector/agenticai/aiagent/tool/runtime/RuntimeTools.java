/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The runtime tools registered as beans: offers the enabled ones to the model and executes their
 * calls, the one place a {@link RuntimeTool} is called. An agent instance created without the
 * runtime tools feature (see {@code AgentFeatures#RUNTIME_TOOLS}) has none.
 */
public class RuntimeTools {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeTools.class);

  private record Registration(RuntimeTool tool, ToolDefinition definition) {}

  private final Map<String, Registration> registrationsByName;

  /**
   * Reads an object of a result back as plain JSON values. It has no modules: the data of the
   * object is never interpreted, and a document stays the document the tool returned.
   */
  private static final ObjectMapper PLAIN_READER = JsonMapper.builder().build();

  private final ObjectMapper argumentsMapper;
  private final ObjectMapper resultMapper;
  private final DocumentParameterResolver documentParameterResolver;

  /**
   * @throws IllegalStateException if two tools have the same name, a name is blank or a definition
   *     is not of kind {@link ToolKind#RUNTIME}
   */
  public RuntimeTools(
      List<RuntimeTool> tools,
      ObjectMapper objectMapper,
      DocumentParameterResolver documentParameterResolver) {
    this.registrationsByName = register(tools);
    this.argumentsMapper = RuntimeToolCallImpl.argumentsMapper(objectMapper);
    this.resultMapper =
        objectMapper.copy().registerModule(new SimpleModule().addSerializer(new DocumentAsItIs()));
    this.documentParameterResolver = documentParameterResolver;
  }

  /** The definitions of the enabled tools in registration order. */
  public List<ToolDefinition> enabledDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    if (!agentContext.supports(AgentFeatures.RUNTIME_TOOLS)) {
      return List.of();
    }
    final var context = new RuntimeToolContextImpl(executionContext, agentContext);
    return registrationsByName.values().stream()
        .filter(registration -> registration.tool().isEnabled(context))
        .map(Registration::definition)
        .toList();
  }

  /**
   * Executes the calls of runtime tools offered in this job one after the other and returns one
   * execution per call in call order.
   *
   * <p>A call of a tool that is not enabled when it is executed gets an error result, and so does a
   * {@link RuntimeToolException}. Any other exception propagates.
   */
  public List<RuntimeToolExecution> execute(
      AgentExecutionContext executionContext, AgentContext agentContext, List<ToolCall> calls) {
    final var context = new RuntimeToolContextImpl(executionContext, agentContext);
    return calls.stream().map(call -> execute(context, call)).toList();
  }

  private RuntimeToolExecution execute(RuntimeToolContext context, ToolCall toolCall) {
    final var startedAt = OffsetDateTime.now(ZoneId.systemDefault());
    final var result =
        findEnabled(context, toolCall.name())
            .map(tool -> callTool(context, tool, toolCall))
            .orElseGet(() ->
                RuntimeToolResult.error(
                    "The tool '%s' is not available.".formatted(toolCall.name())));
    final var completedAt = OffsetDateTime.now(ZoneId.systemDefault());
    return new RuntimeToolExecution(toolCall, result, startedAt, completedAt);
  }

  private Optional<RuntimeTool> findEnabled(RuntimeToolContext context, String name) {
    if (!context.agentContext().supports(AgentFeatures.RUNTIME_TOOLS)) {
      return Optional.empty();
    }
    return Optional.ofNullable(registrationsByName.get(name))
        .map(Registration::tool)
        .filter(tool -> tool.isEnabled(context));
  }

  private RuntimeToolResult callTool(
      RuntimeToolContext context, RuntimeTool tool, ToolCall toolCall) {
    return switch (documentParameterResolver.resolveRuntimeCall(
        context.agentContext(), toolCall, tool.definition())) {
      case DocumentParameterResolver.Resolution.Invalid invalid ->
          throw new IllegalStateException(
              "The call '%s' of the tool '%s' is admitted, but its document parameters are invalid: %s"
                  .formatted(toolCall.id(), toolCall.name(), invalid.message()));
      case DocumentParameterResolver.Resolution.Resolved resolved ->
          callTool(context, tool, toolCall, resolved.arguments());
    };
  }

  private RuntimeToolResult callTool(
      RuntimeToolContext context,
      RuntimeTool tool,
      ToolCall toolCall,
      Map<String, Object> arguments) {
    final var runtimeToolCall =
        new RuntimeToolCallImpl(toolCall.id(), toolCall.name(), arguments, argumentsMapper);

    RuntimeToolResult result;
    try {
      result = tool.call(context, runtimeToolCall);
    } catch (RuntimeToolException e) {
      LOGGER.debug("Runtime tool '{}' rejected the call '{}'", toolCall.name(), toolCall.id(), e);
      result = RuntimeToolResult.error(e.getMessage());
    }
    Objects.requireNonNull(
        result, "The runtime tool '%s' returned no result".formatted(toolCall.name()));
    return result instanceof RuntimeToolResult.Success success
        ? RuntimeToolResult.success(success.content().stream().map(this::plainContent).toList())
        : result;
  }

  /**
   * An object block with its object as plain JSON values (maps, lists, strings, numbers, booleans)
   * and its documents at any depth, also those in a record or another object. The agent finds the
   * documents of plain JSON values, and the object is the one a later job reads from the
   * conversation.
   */
  private Content plainContent(Content content) {
    if (!(content instanceof ObjectContent objectContent)) {
      return content;
    }
    try (var buffer = new TokenBuffer(resultMapper, false)) {
      resultMapper.writeValue(buffer, objectContent.content());
      final Object plain = PLAIN_READER.readValue(buffer.asParser(), Object.class);
      return new ObjectContent(plain, objectContent.metadata());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, Registration> register(List<RuntimeTool> tools) {
    final var registrations = new LinkedHashMap<String, Registration>();
    for (final var tool : tools) {
      final var definition = tool.definition();
      final var name = definition.name();
      if (name.isBlank()) {
        throw new IllegalStateException("A runtime tool must have a non-blank name");
      }
      if (definition.kind() != ToolKind.RUNTIME) {
        throw new IllegalStateException(
            "The runtime tool '%s' must declare the kind %s in its definition."
                .formatted(name, ToolKind.RUNTIME));
      }
      if (registrations.putIfAbsent(name, new Registration(tool, definition)) != null) {
        throw new IllegalStateException(
            "More than one runtime tool is named '%s'; runtime tool names must be unique."
                .formatted(name));
      }
    }
    return registrations;
  }

  /** Writes a document as itself, so that the plain JSON values keep the document. */
  private static final class DocumentAsItIs extends JsonSerializer<Document> {

    @Override
    public Class<Document> handledType() {
      return Document.class;
    }

    @Override
    public void serialize(Document document, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeEmbeddedObject(document);
    }
  }
}
