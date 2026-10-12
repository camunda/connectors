/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * A tool the connector runtime runs itself, without activating a BPMN element. A runtime tool is a
 * definition, shown to the model next to the tools of the process, and a handler, run by the agent
 * when the model calls the tool.
 *
 * <p>Register a tool as a Spring bean of this type. Build one with {@link #builder(String)} or
 * implement the interface.
 *
 * <p>The agent runs a call like this:
 *
 * <ol>
 *   <li>The call is looked up among the tools that are {@link #isAvailable available} for the
 *       agent; other calls go to the process.
 *   <li>The {@link DocumentParameterSchema document parameters} of the definition, the schema nodes
 *       with the document format, are checked: an unknown document answers the call with an error
 *       result listing the known documents and the tool does not run. The tool receives the id of
 *       each document, also when the model passed the {@code <doc />} tag of the document; it looks
 *       the document up in {@link RuntimeToolContext#documents()}.
 *   <li>{@link #call} runs. A {@link RuntimeToolException} or an {@link RuntimeToolResult#error
 *       error result} answers the call with an error the model can act on; any other exception
 *       propagates and fails the job.
 *   <li>The content becomes the tool result, the context updates of the result are applied to the
 *       agent context.
 * </ol>
 *
 * <p>The tool must not keep state of its own: an agent can run in any connector worker, one job
 * after the other. State of the agent belongs into the agent context, changed by {@link
 * RuntimeToolResult#withContextUpdate context updates}.
 */
public interface RuntimeTool {

  /**
   * The definition shown to the model. The name is unique among the runtime tools. It must not
   * equal the name of a tool of the process of the agent the runtime tool runs in: the agent then
   * fails with an error. Custom tools should use a vendor-specific prefix.
   */
  ToolDefinition definition();

  /**
   * Whether the tool is offered to the agent of {@code configuration}. An unavailable tool is not
   * shown to the model and its calls are not answered by the agent.
   */
  default boolean isAvailable(AgentConfiguration configuration) {
    return true;
  }

  /** Runs the tool for {@code call}. */
  RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context);

  /** Starts building a tool with the given name. */
  static Builder builder(String name) {
    return new Builder(name, null);
  }

  /** Starts building a tool for a ready-made definition. */
  static Builder builder(ToolDefinition definition) {
    return new Builder(definition.name(), definition);
  }

  /** Runs a call with the arguments the model sent. */
  @FunctionalInterface
  interface Handler {
    RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context);
  }

  /** Runs a call with arguments bound to a type. */
  @FunctionalInterface
  interface TypedHandler<A> {
    RuntimeToolResult call(A arguments, RuntimeToolContext context);
  }

  /** Builds a {@link RuntimeTool} from a name, a description, a schema and a handler. */
  final class Builder {

    private final String name;
    private final @Nullable ToolDefinition definition;
    private @Nullable String description;
    private Map<String, Object> inputSchema = RuntimeToolSchema.object().build();
    private Predicate<AgentConfiguration> availability = configuration -> true;

    private Builder(String name, @Nullable ToolDefinition definition) {
      this.name = Objects.requireNonNull(name, "name must not be null");
      this.definition = definition;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * The JSON schema of the arguments, an object schema. A parameter of schema type {@code
     * document} is a document parameter.
     */
    public Builder inputSchema(Map<String, Object> inputSchema) {
      this.inputSchema = inputSchema;
      return this;
    }

    /** The schema of the arguments. */
    public Builder inputSchema(RuntimeToolSchema schema) {
      this.inputSchema = schema.build();
      return this;
    }

    /** The tool is offered only to agents whose configuration satisfies the predicate. */
    public Builder available(Predicate<AgentConfiguration> availability) {
      this.availability = availability;
      return this;
    }

    /** Completes the tool with a handler that reads the arguments as the model sent them. */
    public RuntimeTool handler(Handler handler) {
      return new DefaultRuntimeTool(buildDefinition(), availability, handler::call);
    }

    /**
     * Completes the tool with a handler that receives the arguments bound to {@code type}, a record
     * or a bean the {@link ObjectMapper} of the context can read. A document parameter is bound to
     * a {@code String}, the id of the document. Arguments that do not match {@code type} answer the
     * call with an error result.
     */
    public <A> RuntimeTool handler(Class<A> type, TypedHandler<A> handler) {
      return new DefaultRuntimeTool(
          buildDefinition(),
          availability,
          (call, context) -> {
            final A arguments = call.argumentsAs(type, context.objectMapper());
            return handler.call(arguments, context);
          });
    }

    private ToolDefinition buildDefinition() {
      if (definition != null) {
        return definition;
      }
      // the canonical constructor keeps the order of the schema; the record builder would copy it
      // into an immutable map of unspecified order
      return new ToolDefinition(
          name, description, Collections.unmodifiableMap(new LinkedHashMap<>(inputSchema)));
    }
  }
}
