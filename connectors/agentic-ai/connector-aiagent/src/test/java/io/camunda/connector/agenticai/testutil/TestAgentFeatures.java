/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.testutil;

import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import java.util.List;

public final class TestAgentFeatures {

  /** The features of a new v2 agent instance with document references. */
  public static final List<String> DOCUMENT_REFERENCES_ON =
      List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS);

  private TestAgentFeatures() {}
}
