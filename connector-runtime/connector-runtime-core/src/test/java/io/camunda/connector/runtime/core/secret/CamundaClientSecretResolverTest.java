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
package io.camunda.connector.runtime.core.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ResolveSecretsCommandStep1;
import io.camunda.client.api.response.ResolveSecretsResponse;
import io.camunda.client.api.response.ResolveSecretsResponse.ResolutionError;
import io.camunda.client.api.response.ResolveSecretsResponse.ResolvedSecret;
import io.camunda.client.api.search.enums.SecretErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamundaClientSecretResolverTest {

  @Mock private CamundaClient camundaClient;
  @Mock private ResolveSecretsCommandStep1 commandStep;
  @Mock private CamundaFuture<ResolveSecretsResponse> future;

  private CamundaClientSecretResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new CamundaClientSecretResolver(camundaClient);
    lenient().when(camundaClient.newResolveSecretsCommand()).thenReturn(commandStep);
    lenient().when(commandStep.references(any(List.class))).thenReturn(commandStep);
    lenient().when(commandStep.send()).thenReturn(future);
  }

  @Test
  void mergesResolvedValues() {
    var resolvedA = resolvedSecret("camunda.secrets.A", "va");
    var resolvedB = resolvedSecret("camunda.secrets.B", "vb");
    var response = mock(ResolveSecretsResponse.class);
    when(response.getResolved()).thenReturn(List.of(resolvedA, resolvedB));
    when(response.getErrors()).thenReturn(List.of());
    when(future.join()).thenReturn(response);

    var result = resolver.resolve(List.of("camunda.secrets.A", "camunda.secrets.B"), null);

    assertThat(result)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("camunda.secrets.A", "va", "camunda.secrets.B", "vb"));
  }

  @Test
  void chunksRequestsAtTwentyReferences() {
    var references = IntStream.range(0, 21).mapToObj(i -> "camunda.secrets.S" + i).toList();
    var emptyResponse = mock(ResolveSecretsResponse.class);
    when(emptyResponse.getResolved()).thenReturn(List.of());
    when(emptyResponse.getErrors()).thenReturn(List.of());
    when(future.join()).thenReturn(emptyResponse);

    resolver.resolve(references, null);

    verify(camundaClient, times(2)).newResolveSecretsCommand();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(commandStep, times(2)).references(captor.capture());
    var chunks = captor.getAllValues();
    assertThat(chunks).hasSize(2);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.size()).isLessThanOrEqualTo(20));
    assertThat(chunks.stream().flatMap(List::stream).distinct().toList()).hasSize(21);
  }

  @ParameterizedTest
  @EnumSource(
      value = SecretErrorCode.class,
      names = {"NOT_FOUND", "ACCESS_DENIED", "INVALID_REFERENCE", "UNREADABLE"})
  void errorCodes_areAllTreatedAsUnresolved(SecretErrorCode code) {
    var error = resolutionError("camunda.secrets.FOO", code);
    var response = mock(ResolveSecretsResponse.class);
    when(response.getResolved()).thenReturn(List.of());
    when(response.getErrors()).thenReturn(List.of(error));
    when(future.join()).thenReturn(response);

    var result = resolver.resolve(List.of("camunda.secrets.FOO"), null);

    assertThat(result).doesNotContainKey("camunda.secrets.FOO");
  }

  @Test
  void transportFailureOrOlderClusterDegradesToPartialMap() {
    when(future.join()).thenThrow(new RuntimeException("simulated transport failure / 404"));

    var result = resolver.resolve(List.of("camunda.secrets.FOO"), null);

    assertThat(result).isEmpty();
  }

  private static ResolvedSecret resolvedSecret(String reference, String value) {
    var resolvedSecret = mock(ResolvedSecret.class);
    lenient().when(resolvedSecret.getReference()).thenReturn(reference);
    lenient().when(resolvedSecret.getValue()).thenReturn(value);
    return resolvedSecret;
  }

  private static ResolutionError resolutionError(String reference, SecretErrorCode code) {
    var error = mock(ResolutionError.class);
    lenient().when(error.getReference()).thenReturn(reference);
    lenient().when(error.getCode()).thenReturn(code);
    lenient().when(error.getMessage()).thenReturn("resolution failed");
    return error;
  }
}
