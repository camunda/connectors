/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.JobHandlerContext;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.spring.client.annotation.JobWorker;

public class AiAgentJobWorker {

  public static final String AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE = "adHocSubProcessElements";
  public static final String AGENT_CONTEXT_VARIABLE = "agentContext";
  public static final String AGENT_RESPONSE_VARIABLE = "agent";
  public static final String TOOL_CALL_RESULTS_VARIABLE = "toolCallResults";
  public static final String PROVIDER_VARIABLE = "provider";
  public static final String DATA_VARIABLE = "data";

  private final SecretProvider secretProvider;
  private final ValidationProvider validationProvider;
  private final DocumentFactory documentFactory;
  private final ObjectMapper objectMapper;
  private final JobWorkerAgentRequestHandler agentRequestHandler;

  public AiAgentJobWorker(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      JobWorkerAgentRequestHandler agentRequestHandler) {
    this.secretProvider = secretProvider;
    this.validationProvider = validationProvider;
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
    this.agentRequestHandler = agentRequestHandler;
  }

  @JobWorker(
      fetchVariables = {
        AD_HOC_SUB_PROCESS_ELEMENT_VARIABLE,
        AGENT_CONTEXT_VARIABLE,
        TOOL_CALL_RESULTS_VARIABLE,
        PROVIDER_VARIABLE,
        DATA_VARIABLE
      },
      type = "io.camunda.agenticai:aiagent-job-worker:1",
      autoComplete = false)
  public void execute(final JobClient jobClient, final ActivatedJob job) {
    // TODO JW check if we can validate the job kind
    final OutboundConnectorContext context =
        new JobHandlerContext(
            job, secretProvider, validationProvider, documentFactory, objectMapper);
    final var request = context.bindVariables(JobWorkerAgentRequest.class);
    final var executionContext = new JobWorkerAgentExecutionContext(jobClient, job, request);

    agentRequestHandler.handleRequest(executionContext);
  }
}
