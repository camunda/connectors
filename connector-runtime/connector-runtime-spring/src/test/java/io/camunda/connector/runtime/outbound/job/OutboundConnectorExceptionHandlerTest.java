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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.secret.FooBarSecretProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboundConnectorExceptionHandlerTest {

  private final OutboundConnectorExceptionHandler handler =
      new OutboundConnectorExceptionHandler(new FooBarSecretProvider());

  private static ActivatedJob jobWithSecretReference() {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn("{\"value\":\"{{secrets.FOO}}\"}");
    when(job.getKey()).thenReturn(1L);
    when(job.getTenantId()).thenReturn("tenant");
    when(job.getBpmnProcessId()).thenReturn("process");
    when(job.getRetries()).thenReturn(3);
    return job;
  }

  @Test
  void handleFinalResultException_secretFilterThrows_returnsSanitizedResultInsteadOfPropagating() {
    var job = jobWithSecretReference();
    SecretFilter throwingFilter =
        name -> {
          throw new IllegalStateException("process-definition lookup failed");
        };

    var result =
        handler.handleFinalResultException(new RuntimeException("boom"), job, throwingFilter);

    assertThat(result.retries()).isEqualTo(2);
    assertThat(result.exception().getMessage()).contains("Fetching secrets failed");
  }

  @Test
  void
      handleFinalResultException_secretProviderThrows_returnsSanitizedResultInsteadOfPropagating() {
    var job = jobWithSecretReference();
    SecretProvider throwingProvider =
        mock(
            SecretProvider.class,
            invocation -> {
              throw new IllegalStateException("secret store unavailable");
            });
    var handlerWithThrowingProvider = new OutboundConnectorExceptionHandler(throwingProvider);

    var result =
        handlerWithThrowingProvider.handleFinalResultException(
            new RuntimeException("boom"), job, SecretFilter.allowAll());

    assertThat(result.retries()).isEqualTo(2);
    assertThat(result.exception().getMessage()).contains("Fetching secrets failed");
  }

  @Test
  void handleFinalResultException_twoArgOverload_stillAvailableForExternalCallers() {
    var job = jobWithSecretReference();

    var result = handler.handleFinalResultException(new RuntimeException("boom"), job);

    assertThat(result.retries()).isEqualTo(0);
    assertThat(result.exception().getMessage()).isEqualTo("boom");
  }

  @Test
  void manageConnectorJobHandlerException_threeArgOverload_stillAvailableForExternalCallers() {
    var job = jobWithSecretReference();

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("boom"), job, Duration.ofSeconds(1));

    assertThat(result.retries()).isEqualTo(2);
  }
}
