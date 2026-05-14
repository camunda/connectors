/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentcoreharness;

import io.camunda.connector.agenticai.aiagent.AgentConnectorFunction;
import io.camunda.connector.agenticai.aiagent.AiAgentSubProcessConnectorResponse;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializer;
import io.camunda.connector.agenticai.aiagent.agent.AgentLimitsValidator;
import io.camunda.connector.agenticai.aiagent.agent.AgentMessagesHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.framework.agentcoreharness.AgentCoreHarnessAdapter;
import io.camunda.connector.agenticai.aiagent.framework.agentcoreharness.HarnessMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.agentcoreharness.HarnessToolConverter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;

/**
 * AgentCore Harness job worker implementation (acting on an ad-hoc sub-process).
 *
 * <p>Drives an AWS Bedrock AgentCore Harness through a re-entrant tool-call loop, exposing the AHSP
 * inner elements as inline_function tools.
 */
@OutboundConnector(
    name = AgentCoreHarnessJobWorker.JOB_WORKER_NAME,
    type = AgentCoreHarnessJobWorker.JOB_WORKER_TYPE,
    inputVariables = {
      AgentCoreHarnessJobWorker.AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE,
      AgentCoreHarnessJobWorker.AGENT_CONTEXT_VARIABLE,
      AgentCoreHarnessJobWorker.TOOL_CALL_RESULTS_VARIABLE,
      AgentCoreHarnessJobWorker.HARNESS_VARIABLE,
      AgentCoreHarnessJobWorker.AUTHENTICATION_VARIABLE,
      AgentCoreHarnessJobWorker.USER_PROMPT_VARIABLE,
      AgentCoreHarnessJobWorker.MAX_ITERATIONS_VARIABLE
    })
public class AgentCoreHarnessJobWorker implements AgentConnectorFunction {

  public static final String JOB_WORKER_NAME = "AgentCore Harness Job Worker";
  public static final String JOB_WORKER_TYPE = "io.camunda.agenticai:agentcore-harness:1";

  public static final String AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE = "adHocSubProcessElements";
  public static final String AGENT_CONTEXT_VARIABLE = "agentContext";
  public static final String TOOL_CALL_RESULTS_VARIABLE = "toolCallResults";
  public static final String HARNESS_VARIABLE = "harness";
  public static final String AUTHENTICATION_VARIABLE = "authentication";
  public static final String USER_PROMPT_VARIABLE = "userPrompt";
  public static final String MAX_ITERATIONS_VARIABLE = "maxIterations";

  private final AgentInitializer agentInitializer;
  private final ConversationStoreRegistry conversationStoreRegistry;
  private final AgentLimitsValidator limitsValidator;
  private final AgentMessagesHandler messagesHandler;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AgentResponseHandler responseHandler;

  public AgentCoreHarnessJobWorker(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AgentResponseHandler responseHandler) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.limitsValidator = limitsValidator;
    this.messagesHandler = messagesHandler;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.responseHandler = responseHandler;
  }

  @Override
  public AiAgentSubProcessConnectorResponse execute(OutboundConnectorContext context)
      throws Exception {
    var request = context.bindVariables(AgentCoreHarnessRequest.class);

    // Create the Harness adapter with request-specific configuration
    try (var asyncClient = createClient(request)) {

      var adapter =
          new AgentCoreHarnessAdapter(
              asyncClient,
              new HarnessMessageConverter(),
              new HarnessToolConverter(),
              request.harness().harnessArn(),
              request.harness().allowedTools());

      // Create a request handler with the Harness adapter
      var requestHandler =
          new JobWorkerAgentRequestHandler(
              agentInitializer,
              conversationStoreRegistry,
              limitsValidator,
              messagesHandler,
              gatewayToolHandlers,
              adapter,
              responseHandler);

      var executionContext =
          new JobWorkerAgentExecutionContext(context.getJobContext(), request.toAgentRequest());

      return requestHandler.handleRequest(executionContext);
    }
  }

  private BedrockAgentCoreAsyncClient createClient(AgentCoreHarnessRequest request) {
    var builder =
        BedrockAgentCoreAsyncClient.builder()
            .region(Region.of(request.harness().effectiveRegion()));

    switch (request.authentication()) {
      case AwsAuthentication.AwsStaticCredentialsAuthentication staticAuth -> {
        var credentials =
            AwsBasicCredentials.create(staticAuth.accessKey(), staticAuth.secretKey());
        builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
      }
      case AwsAuthentication.AwsDefaultCredentialsChainAuthentication ignored ->
          builder.credentialsProvider(DefaultCredentialsProvider.create());
      case AwsAuthentication.AwsApiKeyAuthentication ignored ->
          throw new IllegalArgumentException("API Key authentication is not supported for Harness");
    }

    return builder.build();
  }
}
