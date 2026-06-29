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
import io.camunda.connector.agenticai.sandbox.spi.ExecRequest;
import io.camunda.connector.agenticai.sandbox.spi.ExecResult;
import io.camunda.connector.agenticai.sandbox.spi.FileInfo;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import io.daytona.sdk.Daytona;
import io.daytona.sdk.DaytonaConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live integration test for {@link DaytonaSandboxProvider}.
 *
 * <p>This test is SKIPPED unless the {@code DAYTONA_API_KEY} environment variable is set. It
 * creates a real Daytona sandbox, exercises exec + file I/O, and cleans up by deleting the sandbox
 * in a {@code finally} block. It must NOT be run in CI unless a Daytona API key is explicitly
 * provided.
 *
 * <p>To run locally:
 *
 * <pre>
 *   DAYTONA_API_KEY=... mvn test -pl connectors/agentic-ai -Dtest=DaytonaSandboxProviderIT
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "DAYTONA_API_KEY", matches = ".+")
class DaytonaSandboxProviderIT {

  private static final Logger log = LoggerFactory.getLogger(DaytonaSandboxProviderIT.class);

  @Test
  void createExecAndFileIo_shouldWork() {
    String apiKey = System.getenv("DAYTONA_API_KEY");
    String apiUrl = System.getenv("DAYTONA_API_URL"); // may be null for cloud

    DaytonaSandboxConfiguration cfg =
        new DaytonaSandboxConfiguration(
            new DaytonaConnection(
                apiKey,
                apiUrl,
                null,
                null,
                new AutoStopConfiguration(AutoStopMode.DURATION, "PT15M"),
                null,
                null));

    DaytonaSandboxProviderFactory factory = new DaytonaSandboxProviderFactory();
    DaytonaSandboxProvider provider = (DaytonaSandboxProvider) factory.create(cfg);
    SandboxSpec spec = factory.specFor(cfg);

    String sandboxId = null;
    try (SandboxSession session = provider.create(spec)) {
      sandboxId = session.handle().sessionId();
      log.info("Created sandbox id={}", sandboxId);

      // --- exec: echo hello ---
      ExecResult echoResult =
          session.exec(
              new ExecRequest(
                  "echo hello",
                  null,
                  null,
                  ExecRequest.DEFAULT_TIMEOUT_SECONDS,
                  ExecRequest.DEFAULT_MAX_OUTPUT_BYTES));
      assertThat(echoResult.exitCode()).isEqualTo(0);
      assertThat(echoResult.stdout().trim()).isEqualTo("hello");
      assertThat(echoResult.stderr()).isEmpty();

      // --- fs write + read ---
      byte[] content = "daytona integration test\n".getBytes(StandardCharsets.UTF_8);
      session.fs().write("/tmp/test-it.txt", content);

      byte[] readBack = session.fs().read("/tmp/test-it.txt");
      assertThat(readBack).isEqualTo(content);

      // --- stat ---
      FileInfo info = session.fs().stat("/tmp/test-it.txt");
      assertThat(info.path()).isEqualTo("/tmp/test-it.txt");
      assertThat(info.size()).isEqualTo(content.length);
      assertThat(info.isBinary()).isFalse();

      log.info("Integration test passed for sandbox id={}", sandboxId);
    } finally {
      // Delete the sandbox to avoid orphaned resources
      if (sandboxId != null) {
        deleteSandbox(apiKey, apiUrl, sandboxId);
      }
    }
  }

  private static void deleteSandbox(String apiKey, String apiUrl, String sandboxId) {
    DaytonaConfig.Builder builder = new DaytonaConfig.Builder().apiKey(apiKey);
    if (apiUrl != null && !apiUrl.isBlank()) {
      builder = builder.apiUrl(apiUrl);
    }
    try (Daytona daytona = new Daytona(builder.build())) {
      daytona.get(sandboxId).delete();
      log.info("Deleted sandbox id={}", sandboxId);
    } catch (Exception e) {
      log.warn("Failed to delete sandbox id={} during IT cleanup: {}", sandboxId, e.getMessage());
    }
  }
}
