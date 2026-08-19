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
package io.camunda.connector.feel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.client.api.response.SecretReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The result processor is how the connector runtime substitutes secret values into an evaluation
 * result. Every public evaluate method has to reach it, and it has to see the references the
 * cluster reported, so these tests pin both.
 */
class CamundaClientFeelExpressionEvaluatorResultProcessorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<List<SecretReference>> reported = new ArrayList<>();

  private final EvaluationResultProcessor recordingProcessor =
      (result, referencedSecrets) -> {
        reported.add(referencedSecrets);
        return result instanceof String text ? text.replace("PLACEHOLDER", "substituted") : result;
      };

  @Test
  void appliesTheProcessorToTheUntypedEvaluate() {
    var evaluator = evaluatorReturning("a PLACEHOLDER value", List.of());

    String result = evaluator.evaluate("=x");

    assertThat(result).isEqualTo("a substituted value");
  }

  @Test
  void appliesTheProcessorToTheClassTypedEvaluate() {
    var evaluator = evaluatorReturning("a PLACEHOLDER value", List.of());

    assertThat(evaluator.evaluate("=x", String.class)).isEqualTo("a substituted value");
  }

  @Test
  void appliesTheProcessorToTheJavaTypeTypedEvaluate() {
    var evaluator = evaluatorReturning("a PLACEHOLDER value", List.of());

    assertThat(
            evaluator.<String>evaluate(
                "=x", TypeFactory.defaultInstance().constructType(String.class)))
        .isEqualTo("a substituted value");
  }

  @Test
  void appliesTheProcessorToEvaluateToJson() {
    // Configuration validation reads its credentialRef through this method and nothing else, so a
    // reference there resolves only if the processor runs here too.
    var evaluator = evaluatorReturning("a PLACEHOLDER value", List.of());

    assertThat(evaluator.evaluateToJson("=x")).isEqualTo("\"a substituted value\"");
  }

  @Test
  void handsTheProcessorTheReferencesTheClusterReported() {
    var reference = mock(SecretReference.class);
    when(reference.getSecretName()).thenReturn("TOKEN");
    var evaluator = evaluatorReturning("value", List.of(reference));

    evaluator.evaluateToJson("=x");

    assertThat(reported).containsExactly(List.of(reference));
  }

  @Test
  void handsTheProcessorWhateverTheClusterReportedIncludingNothing() {
    var evaluator = evaluatorReturning("value", null);

    evaluator.evaluateToJson("=x");

    assertThat(reported).containsExactly((List<SecretReference>) null);
  }

  @Test
  void leavesTheResultAloneWithoutAProcessor() {
    var evaluator =
        FeelExpressionEvaluatorBuilder.camundaClient(
                clientReturning("a PLACEHOLDER value", List.of()))
            .objectMapper(objectMapper)
            .build();

    String result = evaluator.evaluate("=x");

    assertThat(result).isEqualTo("a PLACEHOLDER value");
    assertThat(reported).isEmpty();
  }

  @Test
  void processesAStructuredResultBeforeItIsConverted() {
    var evaluator = evaluatorReturning(Map.of("token", "PLACEHOLDER"), List.of());
    EvaluationResultProcessor mapProcessor =
        (result, referencedSecrets) ->
            Map.of(
                "token", ((Map<?, ?>) result).get("token").toString().replace("PLACEHOLDER", "v"));
    var withMapProcessor =
        FeelExpressionEvaluatorBuilder.camundaClient(
                clientReturning(Map.of("token", "PLACEHOLDER"), List.of()))
            .objectMapper(objectMapper)
            .resultProcessor(mapProcessor)
            .build();

    assertThat(withMapProcessor.evaluateToJson("=x")).isEqualTo("{\"token\":\"v\"}");
    assertThat(evaluator).isNotNull();
  }

  private FeelExpressionEvaluator evaluatorReturning(
      Object result, List<SecretReference> referencedSecrets) {
    return FeelExpressionEvaluatorBuilder.camundaClient(clientReturning(result, referencedSecrets))
        .objectMapper(objectMapper)
        .resultProcessor(recordingProcessor)
        .build();
  }

  private static CamundaClient clientReturning(
      Object result, List<SecretReference> referencedSecrets) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var step2 = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    var response = mock(EvaluateExpressionResponse.class);
    when(response.getResult()).thenReturn(result);
    when(response.getReferencedSecrets()).thenReturn(referencedSecrets);
    when(client.newEvaluateExpressionCommand().expression(any())).thenReturn(step2);
    when(step2.send().join()).thenReturn(response);
    return client;
  }
}
