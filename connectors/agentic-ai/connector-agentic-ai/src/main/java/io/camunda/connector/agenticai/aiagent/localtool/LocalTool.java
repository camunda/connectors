/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * A tool the agent executes itself instead of handing the call to the process (for example
 * returning documents, loading a skill or saving a memory). A local tool is a definition, shown to
 * the model next to the tools of the process, and a handler, run by the agent when the model calls
 * the tool.
 *
 * <p>Register a tool as a Spring bean of this type. Build one with {@link #builder(String)} or
 * implement the interface.
 *
 * <p>The agent runs a call like this:
 *
 * <ol>
 *   <li>The call is looked up among the tools that are {@link #isAvailable available} for the
 *       agent; other calls go to the process.
 *   <li>The parameters the {@link ToolDefinition#documentParameters() definition declares as
 *       document parameters} are checked and resolved: an unknown document id answers the call with
 *       an error result listing the known documents and the tool does not run.
 *   <li>{@link #call} runs. A {@link LocalToolException} or an {@link LocalToolResult#error error
 *       result} answers the call with an error the model can act on; any other exception propagates
 *       and fails the job.
 *   <li>The content becomes the tool result, the context updates of the result are applied to the
 *       agent context.
 * </ol>
 *
 * <p>The tool must not keep state of its own: an agent can run in any connector worker, one job
 * after the other. State of the agent belongs into the agent context, changed by {@link
 * LocalToolResult#withContextUpdate context updates}.
 */
public interface LocalTool {

  /**
   * The definition shown to the model. The name is unique among the local tools and reserved: no
   * BPMN element of an agent may use it.
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
  LocalToolResult call(LocalToolCall call, LocalToolContext context);

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
    LocalToolResult call(LocalToolCall call, LocalToolContext context);
  }

  /** Runs a call with arguments bound to a type. */
  @FunctionalInterface
  interface TypedHandler<A> {
    LocalToolResult call(A arguments, LocalToolContext context);
  }

  /** Builds a {@link LocalTool} from a name, a description, a schema and a handler. */
  final class Builder {

    private final String name;
    private final @Nullable ToolDefinition definition;
    private @Nullable String description;
    private Map<String, Object> inputSchema = LocalToolSchema.object().build();
    private List<String> documentParameters = List.of();
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
     * The JSON schema of the arguments, an object schema. Declare document parameters with {@link
     * #documentParameters}.
     */
    public Builder inputSchema(Map<String, Object> inputSchema) {
      this.inputSchema = inputSchema;
      return this;
    }

    /** The schema of the arguments, together with the document parameters it declares. */
    public Builder inputSchema(LocalToolSchema schema) {
      this.inputSchema = schema.build();
      this.documentParameters = schema.documentParameters();
      return this;
    }

    /**
     * The parameters of the schema that carry a document, as JSON pointers into the schema (see
     * {@link ToolDefinition#documentParameters()}).
     */
    public Builder documentParameters(List<String> documentParameters) {
      this.documentParameters = documentParameters;
      return this;
    }

    /** The tool is offered only to agents whose configuration satisfies the predicate. */
    public Builder available(Predicate<AgentConfiguration> availability) {
      this.availability = availability;
      return this;
    }

    /** Completes the tool with a handler that reads the arguments as the model sent them. */
    public LocalTool handler(Handler handler) {
      return new DefaultLocalTool(buildDefinition(), availability, handler::call);
    }

    /**
     * Completes the tool with a handler that receives the arguments bound to {@code type}, a record
     * or a bean the {@link ObjectMapper} of the context can read. A parameter of type {@code
     * Document} receives the document of a declared document parameter. Arguments that do not match
     * {@code type} answer the call with an error result.
     */
    public <A> LocalTool handler(Class<A> type, TypedHandler<A> handler) {
      return new DefaultLocalTool(
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
      return ToolDefinition.builder()
          .name(name)
          .description(description)
          .inputSchema(inputSchema)
          .documentParameters(documentParameters)
          .build();
    }
  }
}
