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
import io.camunda.connector.agenticai.aiagent.agent.AgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.JobHandlerContext;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.spring.client.annotation.JobWorker;

public class AiAgentJobWorker {

  private final SecretProvider secretProvider;
  private final ValidationProvider validationProvider;
  private final DocumentFactory documentFactory;
  private final ObjectMapper objectMapper;
  private final AgentRequestHandler agentRequestHandler;

  public AiAgentJobWorker(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      AgentRequestHandler agentRequestHandler) {
    this.secretProvider = secretProvider;
    this.validationProvider = validationProvider;
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
    this.agentRequestHandler = agentRequestHandler;
  }

  @JobWorker(
      fetchVariables = {
        "adHocSubProcessElements",
        "agentContext",
        "toolCallResults",
        "provider",
        "data"
      },
      type = "io.camunda.agenticai:aiagent-subprocess:1",
      autoComplete = false)
  public AgentResponse execute(final JobClient jobClient, final ActivatedJob job) {
    // TODO JW check if we can validate the job kind
    final OutboundConnectorContext context =
        new JobHandlerContext(
            job, secretProvider, validationProvider, documentFactory, objectMapper);
    final var request = context.bindVariables(JobWorkerAgentRequest.class);

    return null;
  }
}
