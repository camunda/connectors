/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.metrics.MicrometerMetricsRecorder;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.managedscript.ScriptResources.ScriptResource;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.outbound.job.SpringConnectorJobHandler;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** Runs the managed-script connector through the runtime's outbound job handler. */
class ManagedScriptErrorExpressionTest {

  private static final ScheduledExecutorService commandScheduler =
      Executors.newSingleThreadScheduledExecutor();

  @AfterAll
  static void shutdown() {
    commandScheduler.shutdownNow();
  }

  @Test
  void anErrorExpressionOnTheScriptResultRaisesABpmnError() throws Exception {
    final var job =
        job(
            "{\"scriptInput\": {\"amount\": 50, \"balance\": 10}}",
            Map.of(
                "linkedResources",
                "[{\"resourceKey\":\"1\",\"resourceType\":\"ManagedScript\",\"linkName\":\"script\"}]",
                "errorExpression",
                "if error != null then bpmnError(error, \"Not enough funds\") else null"));
    final var jobClient = mock(JobClient.class);
    final var throwError = mock(ThrowErrorCommandStep1.class, RETURNS_DEEP_STUBS);
    when(jobClient.newThrowErrorCommand(any(ActivatedJob.class))).thenReturn(throwError);

    jobHandler(ExecutionResponse.completed(Map.of("error", "INSUFFICIENT_FUNDS")))
        .handle(jobClient, job);

    verify(throwError, timeout(5_000)).errorCode("INSUFFICIENT_FUNDS");
  }

  private static ActivatedJob job(String variables, Map<String, String> headers) throws Exception {
    final var job = mock(ActivatedJob.class);
    final JsonNode node = ConnectorsObjectMapperSupplier.getCopy().readTree(variables);
    lenient().when(job.getKey()).thenReturn(42L);
    lenient().when(job.getType()).thenReturn("io.camunda:managed-script:1");
    lenient().when(job.getRetries()).thenReturn(3);
    lenient().when(job.getVariables()).thenReturn(variables);
    lenient()
        .when(job.getVariablesAsType(any()))
        .thenAnswer(invocation -> ((Class<?>) invocation.getArgument(0)).cast(node));
    lenient().when(job.getCustomHeaders()).thenReturn(headers);
    return job;
  }

  private static SpringConnectorJobHandler jobHandler(ExecutionResponse response) {
    final var provider =
        new ManagedCodeProvider() {
          @Override
          public String name() {
            return "fake";
          }

          @Override
          public void ensureExecutors() {}

          @Override
          public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
            return response;
          }
        };
    final var resources =
        new ScriptResources(
            1,
            (client, key, timeout) -> new ScriptResource("check.py", "def execute(v, c): ..."),
            Duration.ofSeconds(1),
            Duration.ofMillis(10));
    final var clients = mock(PhysicalTenantClientSelector.class);
    when(clients.forJob(any())).thenReturn(mock(CamundaClient.class));
    final var function =
        new ManagedScriptFunction(
            new ManagedScriptJobHandler(
                provider,
                resources,
                ConnectorsObjectMapperSupplier.getCopy(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10)),
            clients);
    final var metricsRecorder = new MicrometerMetricsRecorder(new SimpleMeterRegistry());
    return new SpringConnectorJobHandler(
        metricsRecorder,
        new JobCallbackCommandWrapperFactory(
            BackoffSupplier.newBackoffBuilder().build(), commandScheduler, metricsRecorder),
        new SecretProviderAggregator(List.of()),
        new DefaultValidationProvider(),
        mock(DocumentFactory.class),
        ConnectorsObjectMapperSupplier.getCopy(),
        function,
        job -> SecretFilter.allowAll(),
        IntrinsicFunctionAllowListFactory.disabled(),
        mock(CamundaClient.class, RETURNS_DEEP_STUBS));
  }
}
