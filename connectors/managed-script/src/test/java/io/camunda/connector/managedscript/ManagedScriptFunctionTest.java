/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.managedscript.ManagedScriptJobHandler.Outcome;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedScriptFunctionTest {

  private final ManagedScriptJobHandler handler = mock(ManagedScriptJobHandler.class);
  private final PhysicalTenantClientSelector clients = mock(PhysicalTenantClientSelector.class);
  private final CamundaClient client = mock(CamundaClient.class);
  private final JobContext job = mock(JobContext.class);
  private final OutboundConnectorContext context = mock(OutboundConnectorContext.class);
  private final ManagedScriptFunction function = new ManagedScriptFunction(handler, clients);

  {
    when(context.getJobContext()).thenReturn(job);
    when(context.bindVariables(ManagedScriptRequest.class))
        .thenReturn(new ManagedScriptRequest(Map.of("a", 2)));
    when(clients.forJob(job)).thenReturn(client);
  }

  @Test
  void returnsTheScriptVariablesAsTheConnectorResult() throws Exception {
    when(job.getPhysicalTenantId()).thenReturn("cluster-a");
    when(handler.handle(client, "cluster-a", job, Map.of("a", 2)))
        .thenReturn(new Outcome.Completed(Map.of("sum", 5)));

    assertThat(function.execute(context)).isEqualTo(Map.of("sum", 5));
  }

  @Test
  void keysAJobWithoutPhysicalTenantApartFromATenantNamedDefault() throws Exception {
    when(handler.handle(eq(client), eq(""), eq(job), anyMap()))
        .thenReturn(new Outcome.Completed(Map.of("cluster", "unnamed")));
    when(handler.handle(eq(client), eq("default"), eq(job), anyMap()))
        .thenReturn(new Outcome.Completed(Map.of("cluster", "default")));

    final var withoutTenant = function.execute(context);
    when(job.getPhysicalTenantId()).thenReturn("default");
    final var namedDefault = function.execute(context);

    assertThat(withoutTenant).isEqualTo(Map.of("cluster", "unnamed"));
    assertThat(namedDefault).isEqualTo(Map.of("cluster", "default"));
  }

  @Test
  void keepsTheRetriesAndBackoffOfAFailure() {
    when(handler.handle(any(), any(), any(), anyMap()))
        .thenReturn(
            new Outcome.Failed(
                "PROVISIONING_PENDING", "still being provisioned", 3, Duration.ofSeconds(10)));

    final var thrown =
        catchThrowableOfType(ConnectorRetryException.class, () -> function.execute(context));

    assertThat(thrown.getRetries()).isEqualTo(3);
    assertThat(thrown.getBackoffDuration()).isEqualTo(Duration.ofSeconds(10));
    assertThat(thrown.getErrorCode()).isEqualTo("PROVISIONING_PENDING");
    assertThat(thrown.getMessage()).isEqualTo("still being provisioned");
  }

  @Test
  void leavesRetriesToTheRuntimeAndDropsAZeroBackoff() {
    when(handler.handle(any(), any(), any(), anyMap()))
        .thenReturn(new Outcome.Failed("SCRIPT_ERROR", "boom", null, Duration.ZERO));

    final var thrown =
        catchThrowableOfType(ConnectorRetryException.class, () -> function.execute(context));

    assertThat(thrown.getRetries()).isNull();
    assertThat(thrown.getBackoffDuration()).isNull();
  }
}
