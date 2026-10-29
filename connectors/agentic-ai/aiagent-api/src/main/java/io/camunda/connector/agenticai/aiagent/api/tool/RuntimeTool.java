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
 * A tool the connector runtime runs itself, without activating a BPMN element. A runtime tool is a
 * {@link #definition() definition}, shown to the model next to the tools of the process, and a
 * {@link #call handler}, run by the agent when the model calls the tool. Register a tool as a
 * Spring bean of this type.
 *
 * <p>The tool types of this package, including {@link RuntimeToolDocuments} and {@link
 * AgentDocument}, are {@link Experimental}.
 *
 * <h2>Execution contract</h2>
 *
 * <ul>
 *   <li><b>At least once.</b> An agent job is retried when it fails after the tool ran, for example
 *       when the model call fails, and then runs the call again. Side effects outside the agent,
 *       such as a request to another system, are not undone. A tool must make them idempotent.
 *   <li><b>Concurrency.</b> The calls of one assistant message may run concurrently. Their results
 *       are returned and applied in the order of the calls. {@link #call} and {@link #isEnabled}
 *       may be called from different threads, and a {@link RuntimeToolContext} is safe to read
 *       concurrently. The calls of one message see the same {@link RuntimeToolContext#documents()
 *       documents}, the ones known when the first of them starts: a call must not depend on a
 *       document another call of the same message returned. The documents the results contain are
 *       known to the next turn, as long as their result is in its context window.
 *   <li><b>Bounded run time.</b> A call runs within the agent job, and the framework neither
 *       cancels nor times it out. It must return well within the timeout of the job.
 *   <li><b>Mixed batches.</b> The agent classifies the calls of an assistant message once, when it
 *       receives the message. When the message calls runtime tools and tools of the process, the
 *       job activates the elements of the process only. The calls of runtime tools run later, in
 *       the job that composes the next turn from the results of the process, after all of them
 *       arrived or were cancelled by an interrupting event. When the message calls runtime tools
 *       only, they run in the job that received the message and the model is called again right
 *       away.
 *   <li><b>Enabled tools.</b> A call of a registered tool that is not {@link #isEnabled enabled}
 *       when the call runs gets an error result and never goes to the process.
 *   <li><b>Failures.</b> A {@link RuntimeToolException} gives the call an error result that the
 *       model reads. A tool that finds a failure the model can act on, such as a wrong argument or
 *       a rejected operation, returns {@link RuntimeToolResult#error} or throws it. Any other
 *       exception is unexpected: it fails the job, which is retried like any job.
 * </ul>
 *
 * <p>The tool must not keep state of its own: the agent can run in any connector worker, one job
 * after the other, and the same tool bean serves every agent. A tool receives the services it needs
 * through its constructor.
 *
 * <p>The name of a runtime tool must not equal the name of a tool of a process: the agent then
 * fails with an error, whether the runtime tool is enabled or not. A tool should use a name with a
 * prefix of its own.
 */
@Experimental
public interface RuntimeTool {

  /**
   * The definition shown to the model. The name is unique among the runtime tools. The definition
   * is part of the configuration fingerprint of the agent and of the request sent to the model, so
   * it must be the same on every call of this method.
   *
   * <p>Build the schema with maps of a stable key order, for example {@link
   * java.util.LinkedHashMap}s, as the schema is serialized in that order and the order is part of
   * the request: a map of unspecified order, such as the one {@link java.util.Map#of} returns,
   * serializes differently in different JVMs.
   */
  ToolDefinition definition();

  /**
   * Whether the tool is offered to the model and its calls are run, for the agent of the given
   * execution. A tool that is not enabled is not shown to the model.
   *
   * <p>The agent calls this method when it assembles the tools of a job and again when it runs a
   * call, so it must be cheap and free of side effects.
   *
   * @param context the agent as of this check
   */
  default boolean isEnabled(RuntimeToolContext context) {
    return true;
  }

  /**
   * Runs the tool for {@code call}. The framework calls it only when the tool is enabled.
   *
   * <p>A {@link RuntimeToolException} becomes an error result; any other exception fails the job.
   *
   * @param call the call of the model
   * @param context the agent as of this call
   * @return the result, never {@code null}
   */
  RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context);
}
