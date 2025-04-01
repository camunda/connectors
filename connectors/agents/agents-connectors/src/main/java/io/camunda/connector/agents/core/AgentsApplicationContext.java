/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;

public interface AgentsApplicationContext {
  ObjectMapper objectMapper();

  CamundaClient camundaClient();
}
