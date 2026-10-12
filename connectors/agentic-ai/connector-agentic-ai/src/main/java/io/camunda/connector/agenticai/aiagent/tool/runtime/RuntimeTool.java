/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A tool the connector runtime runs itself, without activating a BPMN element. A runtime tool is a
 * definition, shown to the model next to the tools of the process, and a handler, run by the agent
 * when the model calls the tool.
 *
 * <p>Register a tool as a Spring bean of this type. Build one with {@link #builder(String)} or
 * implement the interface. The tool must not keep state of its own: an agent can run in any
 * connector worker, one job after the other. State of the agent belongs into the agent context,
 * changed by {@link RuntimeToolResult#withContextUpdate context updates}. A tool receives the
 * services it needs, such as an {@link ObjectMapper}, through its constructor.
 *
 * <p>Availability: the agent offers a tool to the model, and answers its calls, only while {@link
 * #isAvailable the tool is available} for the agent execution. The decision must depend only on the
 * configuration of the invocation and on the features of the agent instance, which do not change
 * while a job runs, and must be cheap. An unavailable tool is not shown to the model, and a call of
 * its name is not answered by the agent but goes to the process like the call of any other tool.
 *
 * <p>The agent runs a call like this:
 *
 * <ol>
 *   <li>The call is looked up among the available tools; other calls go to the process.
 *   <li>The {@link DocumentParameterSchema document parameters} of the definition, the schema nodes
 *       with the document format, are checked: an unknown document answers the call with an error
 *       result listing the known documents and the tool does not run. The tool receives the id of
 *       each document, also when the model passed the {@code <doc />} tag of the document; it looks
 *       the document up in {@link RuntimeToolConversation#documentRegistry()}.
 *   <li>{@link #call} runs. A {@link RuntimeToolException} or an {@link RuntimeToolResult#error
 *       error result} answers the call with an error the model can act on; any other exception
 *       propagates and fails the job.
 *   <li>The content becomes the tool result, the context updates of the result are applied to the
 *       agent context.
 * </ol>
 *
 * <p>The name of a runtime tool must not equal the name of a tool of a process: the agent then
 * fails with an error, whether the runtime tool is available or not. A custom tool should use a
 * name with a vendor-specific prefix.
 */
public interface RuntimeTool {

  /** The definition shown to the model. The name is unique among the runtime tools. */
  ToolDefinition definition();

  /**
   * Whether the tool is offered to the agent of the given execution, and its calls are answered by
   * the agent. An unavailable tool is not shown to the model and its calls are not answered by the
   * agent.
   *
   * @param executionContext the invocation of the agent: its configuration, prompts and limits. The
   *     features of the agent instance are not part of it
   * @param agentContext the persisted state of the agent; it holds the features of the agent
   *     instance, for example {@link AgentContext#supportsDocuments()}
   */
  default boolean isAvailable(AgentExecutionContext executionContext, AgentContext agentContext) {
    return true;
  }

  /**
   * Runs the tool for {@code call}.
   *
   * @param call the call of the model; a document parameter holds the id of its document
   * @param executionContext the invocation of the agent; the features of the agent instance are not
   *     part of it, read them from {@link RuntimeToolConversation#toAgentContext()}
   * @param conversation the conversation of the agent as of the call
   */
  RuntimeToolResult call(
      RuntimeToolCall call,
      AgentExecutionContext executionContext,
      RuntimeToolConversation conversation);

  /** Starts building a tool with the given name. */
  static Builder builder(String name) {
    return new Builder(name, null);
  }

  /** Starts building a tool for a ready-made definition. */
  static Builder builder(ToolDefinition definition) {
    return new Builder(definition.name(), definition);
  }

  /** Decides whether a tool is available for an agent execution. */
  @FunctionalInterface
  interface Availability {
    boolean isAvailable(AgentExecutionContext executionContext, AgentContext agentContext);
  }

  /** Runs a call with the arguments the model sent. */
  @FunctionalInterface
  interface Handler {
    RuntimeToolResult call(
        RuntimeToolCall call,
        AgentExecutionContext executionContext,
        RuntimeToolConversation conversation);
  }

  /** Runs a call with arguments bound to a type. */
  @FunctionalInterface
  interface TypedHandler<A> {
    RuntimeToolResult call(
        A arguments, AgentExecutionContext executionContext, RuntimeToolConversation conversation);
  }

  /** Builds a {@link RuntimeTool} from a name, a description, a schema and a handler. */
  final class Builder {

    private final String name;
    private final @Nullable ToolDefinition definition;
    private @Nullable String description;
    private Map<String, Object> inputSchema = RuntimeToolSchema.object().build();
    private Availability availability = (executionContext, agentContext) -> true;

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

    /** The tool is available only for the agent executions that satisfy {@code availability}. */
    public Builder available(Availability availability) {
      this.availability = availability;
      return this;
    }

    /** Completes the tool with a handler that reads the arguments as the model sent them. */
    public RuntimeTool handler(Handler handler) {
      return new DefaultRuntimeTool(buildDefinition(), availability, handler);
    }

    /**
     * Completes the tool with a handler that receives the arguments bound to {@code type}, a record
     * or a bean {@code objectMapper} can read. A document parameter is bound to a {@code String},
     * the id of the document. Arguments that do not match {@code type} answer the call with an
     * error result.
     */
    public <A> RuntimeTool handler(
        Class<A> type, ObjectMapper objectMapper, TypedHandler<A> handler) {
      return new DefaultRuntimeTool(
          buildDefinition(),
          availability,
          (call, executionContext, conversation) ->
              handler.call(call.argumentsAs(type, objectMapper), executionContext, conversation));
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
