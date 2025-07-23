/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.client.api.response.ActivatedJob;

public class JobWorkerAgentJobContext implements AgentJobContext {

  private final ActivatedJob job;

  public JobWorkerAgentJobContext(final ActivatedJob job) {
    this.job = job;
  }

  @Override
  public String bpmnProcessId() {
    return job.getBpmnProcessId();
  }

  @Override
  public long processDefinitionKey() {
    return job.getProcessDefinitionKey();
  }

  @Override
  public long processInstanceKey() {
    return job.getProcessInstanceKey();
  }

  @Override
  public String elementId() {
    return job.getElementId();
  }

  @Override
  public long elementInstanceKey() {
    return job.getElementInstanceKey();
  }

  @Override
  public String tenantId() {
    return job.getTenantId();
  }

  @Override
  public String type() {
    return job.getType();
  }
}
