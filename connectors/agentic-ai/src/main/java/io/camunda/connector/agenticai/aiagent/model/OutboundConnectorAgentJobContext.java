/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

public class OutboundConnectorAgentJobContext implements AgentJobContext {

  private final OutboundConnectorContext context;

  public OutboundConnectorAgentJobContext(OutboundConnectorContext context) {
    this.context = context;
  }

  @Override
  public String bpmnProcessId() {
    return jobContext().getBpmnProcessId();
  }

  @Override
  public long processDefinitionKey() {
    return jobContext().getProcessDefinitionKey();
  }

  @Override
  public long processInstanceKey() {
    return jobContext().getProcessInstanceKey();
  }

  @Override
  public String elementId() {
    return jobContext().getElementId();
  }

  @Override
  public long elementInstanceKey() {
    return jobContext().getElementInstanceKey();
  }

  @Override
  public String tenantId() {
    return jobContext().getTenantId();
  }

  @Override
  public String type() {
    return jobContext().getType();
  }

  private JobContext jobContext() {
    return context.getJobContext();
  }
}
