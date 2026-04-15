/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime;

public abstract class BaseTest {
  protected static final String AGENT_RUNTIME_ARN =
      "arn:aws:bedrock-agentcore:us-east-1:123456789012:runtime/test-agent";
  protected static final String PROMPT = "What is the fraud risk for claim CLM-001?";
  protected static final String SESSION_ID = "test-session-123";
}
