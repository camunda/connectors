/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

/** Well-known process variable names used by the AI Agent connectors. */
public final class AgentProcessVariables {

  /** Input mapping: the LLM provider/model configuration. */
  public static final String PROVIDER = "provider";

  /** Input mapping: the agent request data (prompts, tools, memory, limits, response config). */
  public static final String DATA = "data";

  /** Output: the agent response result variable. */
  public static final String AGENT_RESPONSE = "agent";

  /** Persisted agent state carried across agent iterations. */
  public static final String AGENT_CONTEXT = "agentContext";

  /** Sub-process only: the resolved ad-hoc sub-process tool elements. */
  public static final String AD_HOC_SUB_PROCESS_ELEMENTS = "adHocSubProcessElements";

  /** Sub-process only: tool-call results accumulated for the current iteration. */
  public static final String TOOL_CALL_RESULTS = "toolCallResults";

  /** Sub-process only: the tool call passed into an activated ad-hoc element. */
  public static final String TOOL_CALL = "toolCall";

  /** Sub-process only: the local result variable of an activated ad-hoc element. */
  public static final String TOOL_CALL_RESULT = "toolCallResult";

  private AgentProcessVariables() {}
}
