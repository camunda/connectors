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
package io.camunda.connector.runtime.core.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.JobError;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.core.secret.SecretNotAvailableException;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboundConnectorExceptionHandlerTest {

  private static final Duration NO_BACKOFF = null;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final SecretProvider secretProvider = mock(SecretProvider.class);
  private final OutboundConnectorExceptionHandler handler =
      new OutboundConnectorExceptionHandler(secretProvider);
  private final List<String> requestedKeys = new ArrayList<>();

  private ActivatedJob jobNaming(String variables) {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn(variables);
    try {
      when(job.getVariablesAsType(ObjectNode.class))
          .thenReturn((ObjectNode) OBJECT_MAPPER.readTree(variables));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid test variables", e);
    }
    when(job.getRetries()).thenReturn(3);
    return job;
  }

  /** Answers only for the given names, and records what was asked for. */
  private void holdingOnly(Map<String, String> secrets) {
    when(secretProvider.getSecret(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              String name = invocation.getArgument(0);
              requestedKeys.add(name);
              return secrets.get(name);
            });
  }

  @Test
  void masksALongerSecretThatStartsWithAShorterOne() {
    // Replacing the shorter value first would leave the longer one unmatched and publish its
    // remainder: "x" before "xSUPERSECRET" turns the message into "***SUPERSECRET".
    var job = jobNaming("{\"a\": \"{{secrets.SHORT}}\", \"b\": \"{{secrets.LONG}}\"}");
    holdingOnly(Map.of("SHORT", "x", "LONG", "xSUPERSECRET"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected xSUPERSECRET"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    assertThat(requestedKeys).containsExactly("SHORT", "LONG");
    assertThat(result.exception().getMessage()).isEqualTo("api rejected ***");
  }

  @Test
  void anEmptySecretValueDoesNotCorruptTheMessage() {
    // Replacing "" matches at every position, so a provider answering with one would rewrite the
    // whole message into separators rather than redact anything.
    var job = jobNaming("{\"a\": \"{{secrets.BLANK}}\"}");
    holdingOnly(Map.of("BLANK", ""));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
  }

  @Test
  void aJobDeclaringNoSecretNeverReachesAProvider() {
    // A provider that refuses every lookup would otherwise withhold the error message of a job that
    // had nothing to redact in the first place.
    var job = jobNaming("{\"a\": \"nothing to resolve here\"}");

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    verifyNoInteractions(secretProvider);
    assertThat(result.exception().getMessage()).isEqualTo("api rejected the request");
    assertThat(result.retries()).isEqualTo(2);
  }

  @Test
  void onlyAllowedNamesAreReadBack() {
    var job = jobNaming("{\"a\": \"{{secrets.ALLOWED}}\", \"b\": \"{{secrets.DENIED}}\"}");
    holdingOnly(Map.of("ALLOWED", "allowed-value", "DENIED", "denied-value"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected allowed-value and denied-value"),
            job,
            NO_BACKOFF,
            SecretFilter.allowOnly(List.of(new Secret("ALLOWED", List.of("a")))),
            List.of());

    assertThat(requestedKeys).containsExactly("ALLOWED");
    assertThat(result.exception().getMessage()).isEqualTo("api rejected *** and denied-value");
  }

  @Test
  void readsTheSameAllowedSecretNameOnlyOnceAcrossMultipleFieldPaths() {
    var job = jobNaming("{\"a\": \"secrets.SHARED\", \"b\": \"secrets.SHARED\"}");
    holdingOnly(Map.of("SHARED", "shared-value"));

    handler.manageConnectorJobHandlerException(
        new RuntimeException("api rejected shared-value"),
        job,
        NO_BACKOFF,
        SecretFilter.allowAll(),
        List.of());

    assertThat(requestedKeys).containsExactly("SHARED");
  }

  @Test
  void aFailedMaskingFetchIsNeverPublishedEither() {
    // A provider writes its own message, and it can carry secret material: a bundle that parses as
    // a non-object puts the value it could not coerce into the coercion error.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(secretProvider.getSecret("FOO"))
        .thenThrow(
            new RuntimeException(
                "Could not resolve secrets: MismatchedInputException: Cannot construct instance of"
                    + " `java.util.LinkedHashMap` from String value ('super-secret')"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected super-secret"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage())
        .startsWith("Fetching secrets failed, so the original error cannot be displayed")
        .contains("java.lang.RuntimeException")
        .doesNotContain("super-secret");
  }

  @Test
  void aMaskingFetchThatSaysTheInputIsAtFaultSpendsNoFurtherAttempt() {
    // A provider that refuses every lookup (e.g. legacy resolution switched off) throws for the
    // masking fetch too; retrying will not change that, so this is not a transient failure.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(secretProvider.getSecret("FOO"))
        .thenThrow(new ConnectorInputException("secret 'FOO' was not resolved", null));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.retries()).isZero();
  }

  @Test
  void aJobWhoseOwnFailureSaysTheInputIsAtFaultSpendsNoFurtherAttemptEither() {
    // The job's failure is still whatever it was: an input that will never bind must not become
    // retryable just because reading the values to redact it happened to time out.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(secretProvider.getSecret("FOO")).thenThrow(new RuntimeException("timed out"));

    var result =
        handler.manageConnectorJobHandlerException(
            new ConnectorInputException("secret 'FOO' is not available", null),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.retries()).isZero();
  }

  @Test
  void aMaskingFetchThatFailedTransientlyKeepsTheJobsRemainingAttempts() {
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    when(secretProvider.getSecret("FOO")).thenThrow(new RuntimeException("timed out"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected the request"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.retries()).isEqualTo(2);
  }

  @Test
  void aRefusalTheRuntimeWroteItselfKeepsItsMessage() {
    // Withholding arbitrary provider text is not a reason to withhold text written to be read: a
    // count of unreadable secrets names what an operator has to go and check.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\", \"b\": \"{{secrets.BAR}}\"}");
    holdingOnly(Map.of("FOO", "foo-value"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected foo-value"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage())
        .contains("1 of the 2 secrets this job's input names could not be read back")
        .doesNotContain("foo-value");
  }

  @Test
  void aJobThatFailedOnAMissingSecretKeepsItsOwnMessage() {
    // The one case where a declared name is expected back empty: substitution is what threw, so
    // nothing of that secret is in the message and the message names what an operator must create.
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    holdingOnly(Map.of());

    var result =
        handler.manageConnectorJobHandlerException(
            new SecretNotAvailableException("FOO"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage())
        .isEqualTo("Secret with name 'FOO' is not available");
  }

  @Test
  void aJobErrorsMessageAndVariablesAreRedacted() {
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    holdingOnly(Map.of("FOO", "bar"));

    var masked =
        (JobError)
            handler.maskConnectorError(
                new JobError("rejected bar", Map.of("detail", "rejected bar"), 0, null),
                job,
                SecretFilter.allowAll(),
                List.of());

    assertThat(masked.message()).isEqualTo("rejected ***");
    assertThat(masked.variables()).containsEntry("detail", "rejected ***");
    // the author's retry decision survives redaction
    assertThat(masked.retries()).isZero();
  }

  @Test
  void aBpmnErrorKeepsItsCodeButLosesItsSecret() {
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    holdingOnly(Map.of("FOO", "bar"));

    var masked =
        (BpmnError)
            handler.maskConnectorError(
                new BpmnError("bar", "rejected bar", Map.of("detail", "rejected bar")),
                job,
                SecretFilter.allowAll(),
                List.of());

    // boundary events match on the code, so it keeps a value equal to the secret's
    assertThat(masked.code()).isEqualTo("bar");
    assertThat(masked.message()).isEqualTo("rejected ***");
    assertThat(masked.variables()).containsEntry("detail", "rejected ***");
  }

  @Test
  void anErrorCarryingNothingToRedactIsLeftAlone() {
    // no message and no variables, so the read is skipped and an unreadable store costs nothing
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");

    var masked =
        handler.maskConnectorError(
            new BpmnError("AUTH_FAILED", null, null), job, SecretFilter.allowAll(), List.of());

    verifyNoInteractions(secretProvider);
    assertThat(((BpmnError) masked).message()).isNull();
  }

  @Test
  void aSecretThatRotatedBetweenBindAndMaskingIsStillRedacted() {
    // the re-read returns what the store holds now, so the value the message carries is only
    // known from what was actually substituted into the job's input
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    holdingOnly(Map.of("FOO", "new-value"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected old-value"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of("old-value"));

    assertThat(result.exception().getMessage()).isEqualTo("api rejected ***");
  }

  @Test
  void aCapturedValueDoesNotSubstituteForAFailedReRead() {
    // a job can declare a secret it never got around to binding, so captures are additive to a
    // complete read rather than a stand-in for one
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\", \"b\": \"{{secrets.BAR}}\"}");
    holdingOnly(Map.of("FOO", "foo-value"));

    var result =
        handler.manageConnectorJobHandlerException(
            new RuntimeException("api rejected bar-value"),
            job,
            NO_BACKOFF,
            SecretFilter.allowAll(),
            List.of("foo-value"));

    assertThat(result.exception().getMessage())
        .startsWith("Fetching secrets failed, so the original error cannot be displayed")
        .doesNotContain("bar-value");
  }

  @Test
  void anErrorExpressionErrorRedactsARotatedSecretToo() {
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    holdingOnly(Map.of("FOO", "new-value"));

    var masked =
        (JobError)
            handler.maskConnectorError(
                new JobError("rejected old-value", Map.of(), 0, null),
                job,
                SecretFilter.allowAll(),
                List.of("old-value"));

    assertThat(masked.message()).isEqualTo("rejected ***");
  }

  @Test
  void aFinalResultFailureIsRedactedAndUnretryable() {
    var job = jobNaming("{\"a\": \"{{secrets.FOO}}\"}");
    holdingOnly(Map.of("FOO", "bar"));

    var result =
        handler.handleFinalResultException(
            new RuntimeException("result expression saw bar"),
            job,
            SecretFilter.allowAll(),
            List.of());

    assertThat(result.exception().getMessage()).isEqualTo("result expression saw ***");
    assertThat(result.retries()).isZero();
  }
}
