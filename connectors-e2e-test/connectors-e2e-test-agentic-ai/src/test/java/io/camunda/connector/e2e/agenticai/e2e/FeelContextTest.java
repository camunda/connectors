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
package io.camunda.connector.e2e.agenticai.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.v2.AgenticAiCredentialConfigurations.VertexAiCredential;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeelContextTest {

  @Test
  void vertexFixtureEvaluatesBeforeRuntimeResolvesServiceAccountSecret() throws Exception {
    var provider = AiAgentE2ETestIT.googleGeminiVertexAiV2("gemini-test", "global");
    var expression =
        provider.properties().get("provider.googleGemini.backend.googleVertexAi.credential");
    var credential = new LocalFeelExpressionEvaluator().evaluate(expression, Map.of());
    var mapper = ConnectorsObjectMapperSupplier.getCopy();
    var jobVariables = mapper.valueToTree(Map.of("credential", credential));
    var serviceAccountJson =
        """
        {"type":"service_account","private_key":"quoted \\"value\\"\\nnext line"}
        """;
    var secrets =
        Map.of(
            "GOOGLE_VERTEX_AI_PROJECT_ID",
            "project",
            "GOOGLE_VERTEX_AI_SERVICE_ACCOUNT",
            serviceAccountJson);

    SecretUtil.replaceSecrets(
        jobVariables, null, (secret, context) -> secrets.get(secret.secretName()));
    var bound = mapper.treeToValue(jobVariables.path("credential"), VertexAiCredential.class);

    assertThat(bound.projectId()).isEqualTo("project");
    assertThat(bound.region()).isEqualTo("global");
    assertThat(((ServiceAccountCredentialsAuthentication) bound.authentication()).jsonKey())
        .isEqualTo(serviceAccountJson);
  }

  @Test
  void serializesServiceAccountJsonAsAnEvaluatableFeelContext() {
    var serviceAccountJson =
        """
        {
          "type": "service_account",
          "private_key": "-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\\n-----END PRIVATE KEY-----\\n"
        }
        """;
    var credential =
        Map.of(
            "projectId",
            "test-project",
            "region",
            "europe-west1",
            "authentication",
            Map.of("type", "serviceAccountCredentials", "jsonKey", serviceAccountJson));

    var result = new LocalFeelExpressionEvaluator().evaluate(FeelContext.of(credential), Map.of());

    assertThat(result).isEqualTo(credential);
  }
}
