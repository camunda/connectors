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
package io.camunda.connector.runtime.outbound.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Secrets are masked out of error output by re-resolving them, so this handler has to resolve
 * against the same scope {@code JobHandlerContext} used when it substituted them. If the two
 * disagree, a secret resolved for one engine would not be recognized here and would leak into the
 * error payload verbatim.
 */
class OutboundConnectorExceptionHandlerTest {

  private final SecretProvider secretProvider = mock(SecretProvider.class);
  private final OutboundConnectorExceptionHandler handler =
      new OutboundConnectorExceptionHandler(secretProvider);

  private static ActivatedJob jobOnEngine(String physicalTenantId) {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn("{\"token\": \"{{secrets.FOO}}\"}");
    when(job.getTenantId()).thenReturn("my-tenant");
    when(job.getBpmnProcessId()).thenReturn("my-process");
    when(job.getPhysicalTenantId()).thenReturn(physicalTenantId);
    return job;
  }

  private SecretContext captureSecretContext() {
    var captor = ArgumentCaptor.forClass(SecretContext.class);
    verify(secretProvider).fetchAll(any(), captor.capture());
    return captor.getValue();
  }

  @Test
  void manageConnectorJobHandlerException_resolvesSecretsAgainstTheJobsPhysicalTenant() {
    var job = jobOnEngine("engine-1");
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("secret-value"));

    handler.manageConnectorJobHandlerException(
        new RuntimeException("boom"), job, Duration.ofSeconds(1), SecretFilter.allowAll());

    assertThat(captureSecretContext())
        .isEqualTo(new SecretContext("my-tenant", "my-process", "engine-1"));
  }

  @Test
  void handleFinalResultException_resolvesSecretsAgainstTheJobsPhysicalTenant() {
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(1);
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("secret-value"));

    handler.handleFinalResultException(new RuntimeException("boom"), job, SecretFilter.allowAll());

    assertThat(captureSecretContext())
        .isEqualTo(new SecretContext("my-tenant", "my-process", "engine-1"));
  }

  @Test
  void handleFinalResultException_masksASecretScopedToTheJobsEngine() {
    // the payoff of scoping correctly: this provider only knows the secret under engine-1, so
    // resolving with the wrong physical tenant leaves the value unmasked in the error payload
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(1);
    SecretProvider engineScopedProvider =
        new SecretProvider() {
          @Override
          public String getSecret(String name, SecretContext context) {
            return "FOO".equals(name) && "engine-1".equals(context.physicalTenantId())
                ? "super-secret"
                : null;
          }
        };

    var result =
        new OutboundConnectorExceptionHandler(engineScopedProvider)
            .handleFinalResultException(
                new RuntimeException("failed talking to https://api?key=super-secret"),
                job,
                SecretFilter.allowAll());

    assertThat(result.responseValue().toString()).doesNotContain("super-secret");
  }
}
