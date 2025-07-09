/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

public interface AgentJobContext {
  String bpmnProcessId();

  long processDefinitionKey();

  long processInstanceKey();

  String elementId();

  long elementInstanceKey();

  String tenantId();

  String type();
}
