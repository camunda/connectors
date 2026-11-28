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
package io.camunda.connector.e2e.probes;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProbeOutcome;
import io.camunda.client.api.response.ProbeResult;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.secretprovider.discovery.enabled=false",
      "SECRET_E2E_PROBE_PASSWORD=valid"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
public class ConfigurationValidationProbeTests {

  private static final String PROBE_ID =
      "validate-configuration/" + TestCredentialValidator.CONFIGURATION_ID;

  @Autowired CamundaClient camundaClient;

  private ProbeResult probe(String probeId, Map<String, Object> input) {
    return camundaClient
        .newCreateProbeCommand()
        .probeId(probeId)
        .input(input)
        .requestTimeout(Duration.ofSeconds(20))
        .send()
        .join();
  }

  @Test
  void answersSuccessForAValidConfiguration() {
    var result = probe(PROBE_ID, Map.of("password", "valid"));

    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(result.getData()).isEqualTo(Map.of("status", "SUCCESS"));
  }

  @Test
  void answersFailureWithACodeForAnInvalidConfiguration() {
    var result = probe(PROBE_ID, Map.of("password", "wrong"));

    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(result.getData())
        .isEqualTo(
            Map.of("status", "FAILURE", "code", "UNAUTHORIZED", "message", "Wrong password."));
  }

  @Test
  void answersErrorWhenTheValidatorThrows() {
    var result = probe(PROBE_ID, Map.of("password", "anything else"));

    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(result.getData())
        .isEqualTo(
            Map.of(
                "status",
                "FAILURE",
                "code",
                "ERROR",
                "message",
                "Validation could not be completed."));
  }

  @Test
  void replacesLegacySecretsFromTheRuntimesOwnProviders() {
    var result = probe(PROBE_ID, Map.of("password", "{{secrets.E2E_PROBE_PASSWORD}}"));

    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(result.getData()).isEqualTo(Map.of("status", "SUCCESS"));
  }
}
