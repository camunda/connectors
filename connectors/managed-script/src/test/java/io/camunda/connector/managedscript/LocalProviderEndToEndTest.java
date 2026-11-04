/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.managedscript.ManagedScriptJobHandler.Outcome;
import io.camunda.connector.managedscript.ScriptResources.ScriptResource;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import io.camunda.connector.managedscript.local.LocalManagedCodeProvider;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A script linked to a task, run by the job handler with the {@code local} provider, on the
 * interpreters of the host: skipped when the one a test needs is not installed.
 */
class LocalProviderEndToEndTest {

  private static final long SCRIPT_KEY = 2251799813685300L;
  private static final String LINKED_RESOURCES =
      "[{\"resourceKey\":\"%d\",\"resourceType\":\"ManagedScript\",\"linkName\":\"script\"}]"
          .formatted(SCRIPT_KEY);

  @TempDir Path directory;

  private final CamundaClient client = mock(CamundaClient.class);

  @Test
  void runsANodeScriptLinkedToATask() {
    final var handler =
        handler(
            "sum.js",
            "export function execute(variables, context) {"
                + " return { sum: variables.a + variables.b, element: context.elementId }; }");

    final var outcome = handler.handle(client, "default", job(), Map.of("a", 2, "b", 3));

    assertThat(outcome)
        .isEqualTo(new Outcome.Completed(Map.of("sum", 5, "element", "compute-sum")));
  }

  @Test
  void runsAPythonScriptLinkedToATask() {
    final var handler =
        handler(
            "sum.py",
            "def execute(variables, context):\n"
                + "    return {'sum': variables['a'] + variables['b']}\n");

    final var outcome = handler.handle(client, "default", job(), Map.of("a", 2, "b", 3));

    assertThat(outcome).isEqualTo(new Outcome.Completed(Map.of("sum", 5)));
  }

  @Test
  void failsAScriptThatThrowsWithItsErrorCode() {
    final var handler =
        handler("fail.js", "export function execute() { throw new Error('no stock'); }");

    final var outcome = handler.handle(client, "default", job(), Map.of());

    assertThat(outcome).isInstanceOf(Outcome.Failed.class);
    assertThat(((Outcome.Failed) outcome).code()).isEqualTo("SCRIPT_ERROR");
    assertThat(((Outcome.Failed) outcome).message()).contains("no stock");
  }

  private ManagedScriptJobHandler handler(String fileName, String source) {
    final var provider =
        new LocalManagedCodeProvider(directory, ConnectorsObjectMapperSupplier.getCopy());
    final var language =
        fileName.endsWith(".py") ? ScriptLanguage.PYTHON : ScriptLanguage.JAVASCRIPT;
    assumeTrue(isInstalled(provider, language), "no interpreter for " + language.language());
    return new ManagedScriptJobHandler(
        provider,
        new ScriptResources(
            16,
            (camundaClient, resourceKey, timeout) -> new ScriptResource(fileName, source),
            Duration.ofSeconds(1),
            Duration.ofMillis(10)),
        ConnectorsObjectMapperSupplier.getCopy(),
        Duration.ofSeconds(30),
        Duration.ofSeconds(10));
  }

  /** Whether the provider has an interpreter for the language: the others fail their scripts. */
  private static boolean isInstalled(LocalManagedCodeProvider provider, ScriptLanguage language) {
    try {
      provider.ensureExecutors();
    } catch (ProvisioningException e) {
      return false;
    }
    final var probe = new ExecutionRequest("0", "", Map.of(), Map.of(), 1_000);
    final var response = provider.execute(language, probe);
    return response.error() == null
        || !ErrorCode.PLATFORM_REJECTED.name().equals(response.error().code());
  }

  private static JobContext job() {
    final var job = mock(JobContext.class);
    lenient().when(job.getCustomHeaders()).thenReturn(Map.of("linkedResources", LINKED_RESOURCES));
    lenient().when(job.getJobKey()).thenReturn(42L);
    lenient().when(job.getRetries()).thenReturn(3);
    lenient().when(job.getTenantId()).thenReturn("<default>");
    lenient().when(job.getBpmnProcessId()).thenReturn("order-process");
    lenient().when(job.getProcessDefinitionVersion()).thenReturn(3);
    lenient().when(job.getProcessInstanceKey()).thenReturn(100L);
    lenient().when(job.getElementId()).thenReturn("compute-sum");
    lenient().when(job.getElementInstanceKey()).thenReturn(101L);
    return job;
  }
}
