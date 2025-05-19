package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallResult;
import java.util.List;

public record AgentInitializationResult(
    AgentContext agentContext, List<ToolCallResult> toolCallResults, AgentResponse agentResponse) {}
