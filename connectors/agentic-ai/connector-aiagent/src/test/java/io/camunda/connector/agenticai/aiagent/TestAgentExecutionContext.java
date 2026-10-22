/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

/** Creates {@link AgentExecutionContext}s for tests that only care about the configuration. */
public final class TestAgentExecutionContext {

  private TestAgentExecutionContext() {}

  /** An execution context whose {@link AgentExecutionContext#configuration()} is the given one. */
  public static AgentExecutionContext withConfiguration(AgentConfiguration configuration) {
    final var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration()).thenReturn(configuration);
    return executionContext;
  }
}
