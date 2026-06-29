/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.daytona;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoStopConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.AutoStopMode;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration.DaytonaConnection;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import org.junit.jupiter.api.Test;

class DaytonaSandboxProviderFactoryTest {

  private final DaytonaSandboxProviderFactory factory = new DaytonaSandboxProviderFactory();

  @Test
  void specFor_propagatesStartupScript() {
    var config =
        new DaytonaSandboxConfiguration(
            new DaytonaConnection(
                "api-key",
                null,
                "snap-v1",
                "pip install ruff",
                new AutoStopConfiguration(AutoStopMode.DURATION, "PT15M"),
                null,
                null));

    SandboxSpec spec = factory.specFor(config);

    assertThat(spec.startupScript()).isEqualTo("pip install ruff");
    assertThat(spec.snapshot()).isEqualTo("snap-v1");
    assertThat(spec.autoStopMinutes()).isEqualTo(15);
  }

  @Test
  void specFor_startupScriptIsNullWhenNotConfigured() {
    var config =
        new DaytonaSandboxConfiguration(
            new DaytonaConnection("api-key", null, null, null, null, null, null));

    SandboxSpec spec = factory.specFor(config);

    assertThat(spec.startupScript()).isNull();
  }
}
