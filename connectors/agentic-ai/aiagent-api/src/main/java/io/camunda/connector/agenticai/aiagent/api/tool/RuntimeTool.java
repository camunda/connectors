/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.annotation.Experimental;

/**
 * A tool the connector runtime executes itself, without activating a BPMN element. Register it as a
 * Spring bean of this type. The tool types of this package, including {@link RuntimeToolDocuments},
 * {@link AgentDocument} and {@link RuntimeToolSchemas}, are {@link Experimental}.
 *
 * <h2>Execution contract</h2>
 *
 * <ul>
 *   <li><b>At least once.</b> A job that fails after the call was executed is retried and executes
 *       the call again, so side effects outside the agent must be idempotent.
 *   <li><b>Concurrency.</b> The calls of one assistant message may be executed concurrently, their
 *       results are applied in call order. {@link #call} and {@link #isEnabled} may be called from
 *       different threads. The calls of one message see the same {@link
 *       RuntimeToolContext#documents() documents}, the ones known when the first of them starts: a
 *       call must not depend on a document another call of the same message returned.
 *   <li><b>Bounded execution time.</b> A call is executed within the agent job and is neither
 *       cancelled nor timed out by the framework, so it must return well within the job timeout.
 *   <li><b>Mixed batches.</b> When an assistant message calls runtime tools and tools of the
 *       process, the job activates the process elements only; the runtime tool calls are executed
 *       in the job that composes the next turn from the results of the process. When it calls
 *       runtime tools only, they are executed in the same job and the model is called again right
 *       away.
 *   <li><b>Enabled tools.</b> A call of a registered tool that is not {@link #isEnabled enabled}
 *       when the call is executed gets an error result and never goes to the process.
 *   <li><b>Failures.</b> A failure the model can act on is returned as {@link
 *       RuntimeToolResult#error} or thrown as a {@link RuntimeToolException}. Any other exception
 *       fails the job.
 * </ul>
 *
 * <p>A tool must not keep state of its own: any connector worker can run the agent, and one bean
 * serves every agent.
 *
 * <p>The name of a runtime tool must not equal the name of a tool of a process while the runtime
 * tool is {@link #isEnabled enabled}: the agent fails with an error. A tool that is not enabled
 * reserves no name. Use a name with a prefix of your own. The prefix {@code c8__} is meant for the
 * tools the connector ships; no prefix is checked or reserved, only the exact name.
 */
@Experimental
public interface RuntimeTool {

  /**
   * The definition shown to the model, built with {@link ToolDefinition#runtime}. The name is
   * unique among the runtime tools. The definition is part of the configuration fingerprint and of
   * the model request, so it must be the same on every call.
   *
   * <p>Build the schema with maps of a stable key order, such as {@link java.util.LinkedHashMap},
   * not {@link java.util.Map#of}, which serializes differently in different JVMs. {@link
   * RuntimeToolSchemas} creates the schema of a parameter that carries a document.
   */
  ToolDefinition definition();

  /**
   * Whether the tool is shown to the model and its calls are executed. The agent calls this method
   * on every job and again when it executes a call, so it must be cheap and free of side effects.
   * An exception fails the job.
   *
   * <p>Keep the answer stable while an agent instance runs. The tools are part of every model
   * request, so a change shows the model a different set of tools and invalidates the prompt cache
   * of the provider.
   */
  default boolean isEnabled(RuntimeToolContext context) {
    return true;
  }

  /**
   * Handles {@code call}, only when the tool is enabled and the document parameters of the call
   * name known documents: any other call gets an error result and does not reach the tool. A {@link
   * RuntimeToolException} becomes an error result; any other exception fails the job.
   *
   * @return the result, never {@code null}
   */
  RuntimeToolResult call(RuntimeToolContext context, RuntimeToolCall call);
}
