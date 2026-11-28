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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProbeOutcome;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** A hybrid runtime that must answer alone registers its probe IDs under its own prefix. */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.secretprovider.discovery.enabled=false",
      "camunda.connector.probes.probe-id-prefix=hybrid-e2e/validate-configuration/"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@SlowTest
public class HybridProbeIdTests {

  @Autowired CamundaClient camundaClient;

  @Test
  void answersTheProbeIdWithItsOwnPrefix() {
    var result =
        camundaClient
            .newCreateProbeCommand()
            .probeId(
                "hybrid-e2e/validate-configuration/" + TestCredentialValidator.CONFIGURATION_ID)
            .input(Map.of("password", "valid"))
            .requestTimeout(Duration.ofSeconds(20))
            .send()
            .join();

    assertThat(result.getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(result.getData()).isEqualTo(Map.of("status", "SUCCESS"));
  }

  @Test
  void doesNotAnswerTheDefaultProbeId() {
    var command =
        camundaClient
            .newCreateProbeCommand()
            .probeId("validate-configuration/" + TestCredentialValidator.CONFIGURATION_ID)
            .input(Map.of("password", "valid"))
            .requestTimeout(Duration.ofSeconds(3))
            .send();

    // The gateway sometimes times out at the same deadline as the engine and answers 504
    // DEADLINE_EXCEEDED instead of 404 NO_WORKER; either way no worker collected the probe.
    assertThatThrownBy(command::join)
        .isInstanceOfSatisfying(
            ProblemException.class,
            problem ->
                assertThat(problem.details().getTitle()).isIn("NO_WORKER", "DEADLINE_EXCEEDED"));
  }
}
