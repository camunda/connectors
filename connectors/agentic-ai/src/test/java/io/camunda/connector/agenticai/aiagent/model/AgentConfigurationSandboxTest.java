/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.DaytonaConnection;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DisabledSandboxConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link AgentConfiguration#sandboxConfiguration()} treats {@link
 * DisabledSandboxConfiguration} and {@code null} as "no sandbox", and returns a present Optional
 * only for an enabled provider like Daytona.
 */
class AgentConfigurationSandboxTest {

  @Test
  void sandboxConfiguration_isEmptyWhenNull() {
    var config = new AgentConfiguration(null, null, null, null, null, null, null, null);
    assertThat(config.sandboxConfiguration()).isEmpty();
  }

  @Test
  void sandboxConfiguration_isEmptyForDisabled() {
    var config =
        new AgentConfiguration(
            null, null, null, null, null, null, null, new DisabledSandboxConfiguration());
    assertThat(config.sandboxConfiguration()).isEmpty();
  }

  @Test
  void sandboxConfiguration_isPresentForDaytona() {
    var daytona =
        new DaytonaSandboxConfiguration(
            new DaytonaConnection("key", null, null, null, null, null, null));
    var config = new AgentConfiguration(null, null, null, null, null, null, null, daytona);
    assertThat(config.sandboxConfiguration()).isPresent().contains(daytona);
  }
}
