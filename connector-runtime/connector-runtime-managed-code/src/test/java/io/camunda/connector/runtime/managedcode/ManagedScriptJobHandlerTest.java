/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.managedcode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManagedScriptJobHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ManagedScriptControlPlane controlPlane = mock(ManagedScriptControlPlane.class);
  private final LocalProcessScriptExecutor executor = mock(LocalProcessScriptExecutor.class);
  private final CamundaClient camundaClient = mock(CamundaClient.class);
  private final JobClient jobClient = mock(JobClient.class);
  private final ActivatedJob job = mock(ActivatedJob.class);
  private final ManagedScriptJobHandler handler =
      new ManagedScriptJobHandler(controlPlane, executor, objectMapper, Duration.ofSeconds(1));

  @BeforeEach
  void setUp() {
    when(job.getKey()).thenReturn(42L);
    when(job.getProcessDefinitionKey()).thenReturn(100L);
    when(job.getElementId()).thenReturn("script");
    when(job.getRetries()).thenReturn(3);
    when(job.getVariablesAsMap()).thenReturn(Map.of("a", 2, "b", 3));
    when(job.getTenantId()).thenReturn("<default>");
  }

  @Test
  void completesJobWithScriptVariables() throws Exception {
    final var invocation = invocation(ManagedScriptInvocation.Status.READY);
    when(controlPlane.getInvocation(camundaClient, "default", 100L, "script"))
        .thenReturn(invocation);
    when(executor.execute(any(), any(), any(), any()))
        .thenReturn(
            new LocalProcessScriptExecutor.ExecutionResult(
                objectMapper.readTree(
                    """
                    {"contractVersion":"1","outcome":"COMPLETED","variables":{"sum":5}}
                    """),
                12L,
                "",
                false,
                LocalProcessScriptExecutor.MODE));
    final var complete = mock(CompleteJobCommandStep1.class, RETURNS_SELF);
    when(jobClient.newCompleteCommand(job)).thenReturn(complete);

    handler.handle(camundaClient, "default", jobClient, job);

    verify(complete).variables(Map.of("sum", 5));
    verify(complete).execute();
  }

  @Test
  void defersJobWithoutConsumingRetriesUntilDeploymentIsReady() throws Exception {
    when(controlPlane.getInvocation(camundaClient, "default", 100L, "script"))
        .thenReturn(invocation(ManagedScriptInvocation.Status.DEPLOYING));
    final var failStep1 = mock(FailJobCommandStep1.class);
    final var failStep2 = mock(FailJobCommandStep1.FailJobCommandStep2.class, RETURNS_SELF);
    when(jobClient.newFailCommand(job)).thenReturn(failStep1);
    when(failStep1.retries(3)).thenReturn(failStep2);

    handler.handle(camundaClient, "default", jobClient, job);

    verify(failStep1).retries(3);
    verify(failStep2).retryBackoff(Duration.ofSeconds(1));
    verify(failStep2).execute();
    verifyNoInteractions(executor);
  }

  private static ManagedScriptInvocation invocation(ManagedScriptInvocation.Status status) {
    return new ManagedScriptInvocation(
        status,
        "fake",
        status == ManagedScriptInvocation.Status.READY ? "fake-deployment" : "",
        "javascript",
        "nodejs22",
        1L,
        "sum.js",
        "export function execute() { return {}; }".getBytes(StandardCharsets.UTF_8));
  }
}
