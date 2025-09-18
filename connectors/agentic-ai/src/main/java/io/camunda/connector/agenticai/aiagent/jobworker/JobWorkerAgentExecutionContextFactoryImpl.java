/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.JobHandlerContext;

public class JobWorkerAgentExecutionContextFactoryImpl
    implements JobWorkerAgentExecutionContextFactory {
  private final SecretProvider secretProvider;
  private final ValidationProvider validationProvider;
  private final DocumentFactory documentFactory;
  private final ObjectMapper objectMapper;

  public JobWorkerAgentExecutionContextFactoryImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper) {
    this.secretProvider = secretProvider;
    this.validationProvider = validationProvider;
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
  }

  @Override
  public JobWorkerAgentExecutionContext createExecutionContext(
      final JobClient jobClient, final ActivatedJob job) {
    final OutboundConnectorContext context =
        new JobHandlerContext(
            job, secretProvider, validationProvider, documentFactory, objectMapper);
    final var request = context.bindVariables(JobWorkerAgentRequest.class);
    return new JobWorkerAgentExecutionContext(jobClient, job, request);
  }
}
