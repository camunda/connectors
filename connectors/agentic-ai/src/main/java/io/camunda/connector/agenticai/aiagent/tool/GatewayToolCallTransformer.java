/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

/**
 * Transforms tool calls and tool call results for/from the gateway.
 *
 * <p>When using a gateway, the tool will be a single element for the process context, but for the
 * LLM we need to provide a unique name referencing the tool to call.
 *
 * <p>For example, a gateway element "MyFilesystem" could expose the tools "readFile" and
 * "writeFile". When calling the LLM, we need to provide tools as unique names, such as
 * "MyFilesystem_readFile" and "MyFilesystem_writeFile". The responsibility of this component is to
 * map tool calls to match the process format and to map the tool call results back to the format
 * expected by the LLM.
 *
 * <p>When the LLM returns a tool call, requesting to call "MyFilesystem_readFile", the transformer
 * is expected to transform it into a tool call calling the "MyFilesystem" gateway element and to
 * provide the "readFile" operation plus additional arguments as part of the tool call payload. How
 * this is done varies by gateway implementation.
 *
 * <p>When returning the tool call results to the LLM, the transformer is expected to transform the
 * tool call results back into the format expected by the LLM, e.g. mapping the name back to
 * "MyFilesystem_readFile" and providing the result of the tool call.
 */
public interface GatewayToolCallTransformer {
  List<ToolCall> transformToolCalls(AgentContext agentContext, List<ToolCall> toolCalls);

  List<ToolCallResult> transformToolCallResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults);
}
