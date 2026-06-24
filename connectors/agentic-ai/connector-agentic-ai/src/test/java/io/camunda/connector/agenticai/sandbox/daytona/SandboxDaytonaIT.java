/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.sandbox.daytona.DaytonaClient.DaytonaSandboxInfo;
import io.camunda.connector.agenticai.sandbox.daytona.DaytonaClient.ExecOutcome;
import io.daytona.sdk.Daytona;
import io.daytona.sdk.Sandbox;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Gated integration test — only runs when {@code DAYTONA_API_KEY} is set in the environment.
 *
 * <p>Do NOT run this test locally unless you have a valid Daytona API key and are prepared to
 * provision a real sandbox.
 */
@EnabledIfEnvironmentVariable(named = "DAYTONA_API_KEY", matches = ".+")
class SandboxDaytonaIT {

  @Test
  void createWriteBashReadReconnect() {
    String apiKey = System.getenv("DAYTONA_API_KEY");
    String apiUrl = System.getenv("DAYTONA_API_URL"); // may be null → uses cloud default

    DaytonaClient client = new DaytonaClient();
    DaytonaConnection config = new DaytonaConnection(apiKey, apiUrl, null, null, null, null);

    // 1. Create sandbox
    Daytona daytona = DaytonaClient.buildClient(apiKey, apiUrl);
    DaytonaSandboxInfo info = client.create(daytona, config, "it-test-999", "SandboxElementIT");
    assertThat(info.handle()).isNotBlank();
    assertThat(info.workDir()).isNotBlank();

    // 2. Connect and write a file
    Sandbox sandbox = client.connect(daytona, info.handle());
    String testContent = "hello from integration test\n";
    String testPath = info.workDir() + "/it-test.txt";
    client.fsWrite(sandbox, testPath, testContent.getBytes(StandardCharsets.UTF_8));

    // 3. Read the file back via bash
    ExecOutcome catOutcome = client.exec(sandbox, "cat " + testPath, 30);
    assertThat(catOutcome.exitCode()).isEqualTo(0);
    assertThat(catOutcome.stdout()).contains("hello from integration test");

    // 4. Read the file via fs_read
    byte[] readBytes = client.fsRead(sandbox, testPath);
    assertThat(new String(readBytes, StandardCharsets.UTF_8)).isEqualTo(testContent);

    // 5. Reconnect (simulate a re-entry into the BPMN sandbox element)
    Sandbox reconnected = client.connect(daytona, info.handle());
    ExecOutcome reconnectOutcome = client.exec(reconnected, "echo reconnected", 30);
    assertThat(reconnectOutcome.exitCode()).isEqualTo(0);
    assertThat(reconnectOutcome.stdout()).contains("reconnected");
  }
}
