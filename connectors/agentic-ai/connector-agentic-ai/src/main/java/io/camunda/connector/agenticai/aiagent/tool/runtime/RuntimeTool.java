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

/**
 * A tool the connector runtime runs itself, without activating a BPMN element. A runtime tool is a
 * definition, shown to the model next to the tools of the process, and a handler, run by the agent
 * when the model calls the tool.
 *
 * <p>Register a tool as a Spring bean of this type. The tool must not keep state of its own: an
 * agent can run in any connector worker, one job after the other. State of the agent belongs into
 * the agent context, changed by {@link RuntimeToolResult#withContextUpdate context updates}. A tool
 * receives the services it needs, such as an {@link ObjectMapper}, through its constructor.
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
 *       result and the tool does not run. The tool receives the id of each document, taken from the
 *       {@code <doc />} tag the model passed; it looks the document up in the {@link
 *       AgentContext#documentRegistry() registry} of the {@link
 *       RuntimeToolConversation#agentContext() agent context}. A union parameter (text or a
 *       document, a schema node with {@code anyOf} or {@code oneOf} alternatives of which one is a
 *       document schema) receives the id when the model passed exactly one {@code <doc />} tag, and
 *       any other value as the model passed it; a tool cannot tell a bare id the model wrote as
 *       text from the id of a tag.
 *   <li>{@link #call} runs. A {@link RuntimeToolException} or an {@link RuntimeToolResult#error
 *       error result} answers the call with an error the model can act on; any other exception
 *       propagates and fails the job.
 *   <li>The content becomes the tool result, the context updates of the result are applied to the
 *       agent context, and the content of the documents the result {@link
 *       RuntimeToolResult#withDocumentContent names} is added to the request that follows the tool
 *       results.
 * </ol>
 *
 * <p>The name of a runtime tool must not equal the name of a tool of a process: the agent then
 * fails with an error, whether the runtime tool is available or not. A custom tool should use a
 * name with a vendor-specific prefix.
 */
public interface RuntimeTool {

  /**
   * The definition shown to the model. The name is unique among the runtime tools. The definition
   * is part of the configuration fingerprint and of the request sent to the model: build its maps
   * with a stable key order, for example a {@link java.util.LinkedHashMap}, so that it serializes
   * to the same bytes in every JVM.
   */
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
   *     part of it, read them from {@link RuntimeToolConversation#agentContext()}
   * @param conversation the conversation of the agent as of the call
   */
  RuntimeToolResult call(
      RuntimeToolCall call,
      AgentExecutionContext executionContext,
      RuntimeToolConversation conversation);
}
