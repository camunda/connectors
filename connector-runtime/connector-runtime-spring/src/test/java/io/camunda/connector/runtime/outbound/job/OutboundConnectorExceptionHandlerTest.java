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
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.outbound.ConnectorResult;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * The error message was already masked, but the error variables are copied straight off the
   * original exception. For HTTP connectors those carry the whole response body and headers, so an
   * API that echoes a rejected credential back used to publish the resolved secret as a process
   * variable.
   */
  @Test
  void errorVariables_maskSecretsNestedInAnHttpResponseBody() {
    var errorVariables =
        Map.<String, Object>of(
            "response",
            Map.of(
                "headers",
                Map.of("www-authenticate", "Bearer error=\"invalid_token super-secret\""),
                "body",
                Map.of("errors", List.of(Map.of("detail", "token super-secret is not valid")))));

    var masked = maskedErrorVariables(errorVariables);

    assertThat(masked.toString()).doesNotContain("super-secret").contains("***");
  }

  @Test
  void errorVariables_maskSecretsUsedAsMapKeys() {
    var masked = maskedErrorVariables(Map.of("super-secret", "harmless"));

    assertThat(masked).containsOnlyKeys("***");
  }

  /** Processes branch on these, so rewriting them into strings would change behaviour. */
  @Test
  void errorVariables_leaveNonStringLeavesUntouched() {
    var errorVariables = new HashMap<String, Object>();
    errorVariables.put("status", 401);
    errorVariables.put("retryable", false);
    errorVariables.put("body", null);

    var masked = maskedErrorVariables(errorVariables);

    assertThat(masked).containsEntry("status", 401).containsEntry("retryable", false);
    assertThat(masked).containsKey("body");
    assertThat(masked.get("body")).isNull();
  }

  /** Error variables are supplied by connector authors, so a container may contain itself. */
  @Test
  void errorVariables_terminateOnSelfReferencingContainers() {
    var errorVariables = new HashMap<String, Object>();
    errorVariables.put("detail", "token super-secret rejected");
    errorVariables.put("self", errorVariables);

    var masked = maskedErrorVariables(errorVariables);

    assertThat(masked).containsEntry("detail", "token *** rejected");
    assertThat(masked).containsEntry("self", "[circular reference]");
  }

  /**
   * BPMN error boundary events match on the error code, so masking it would silently stop the error
   * from being caught. Codes come from HTTP statuses and connector-authored constants, not from
   * remote payloads.
   */
  @Test
  void errorCode_isNotMasked() {
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(1);
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("401"));

    var result =
        handler.handleFinalResultException(
            new ConnectorException("401", "Unauthorized"), job, SecretFilter.allowAll());

    assertThat(error(result)).containsEntry("code", "401");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> maskedErrorVariables(Map<String, Object> errorVariables) {
    var job = jobOnEngine("engine-1");
    when(job.getRetries()).thenReturn(1);
    when(secretProvider.fetchAll(any(), any())).thenReturn(List.of("super-secret"));

    var result =
        handler.handleFinalResultException(
            new ConnectorException("401", "Unauthorized", null, errorVariables),
            job,
            SecretFilter.allowAll());

    return (Map<String, Object>) error(result).get("variables");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> error(ConnectorResult.ErrorResult result) {
    return (Map<String, Object>) ((Map<String, Object>) result.responseValue()).get("error");
  }
}
