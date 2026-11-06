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
package io.camunda.connector.runtime.saas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.test.utils.oidc.MockOidcServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Controller reads the executors in use from each instance, without a token (ADR-0008). */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = {SaaSConnectorRuntimeApplication.class},
    properties = {
      "camunda.saas.secrets.projectId=42",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.connector.secretprovider.discovery.enabled=false",
      // As in the application.properties of the bundle, which the test resources replace.
      "management.server.port=0",
      "management.endpoints.web.exposure.include=metrics,health,prometheus,loggers,managedcode",
      "camunda.connector.managed-code.enabled=true",
      "camunda.connector.managed-code.provider=local",
      "camunda.connector.managed-code.deployment-name-prefix=ce-0123-",
      "camunda.client.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.client.auth.token-url=http://localhost:0/not-used",
      "camunda.client.auth.client-id=test",
      "camunda.client.auth.client-secret=test",
      "spring.cloud.gcp.parametermanager.enabled=false"
    })
@ActiveProfiles("test")
class ManagedCodeEndpointTest {

  private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();
  private static final Path LOCAL_DIRECTORY = temporaryDirectory();

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.connector.auth.issuer", OIDC_SERVER::issuer);
    registry.add("camunda.connector.managed-code.local.directory", LOCAL_DIRECTORY::toString);
  }

  @AfterAll
  static void stopOidcServer() {
    OIDC_SERVER.close();
  }

  @MockitoBean(answers = Answers.RETURNS_MOCKS)
  public SaaSSecretConfiguration saaSSecretConfiguration;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  public CamundaClient camundaClient;

  @LocalManagementPort int managementPort;

  @Test
  void reportsTheExecutorsInUseOnTheManagementPortWithoutAToken() throws IOException {
    final var response =
        new RestTemplateBuilder()
            .rootUri("http://localhost:" + managementPort + "/actuator")
            .connectTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .build()
            .getForEntity("/managedcode", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final JsonNode body = new ObjectMapper().readTree(response.getBody());
    assertThat(body.get("provider").asText()).isEqualTo("local");
    assertThat(body.get("deploymentNamePrefix").asText()).isEqualTo("ce-0123-");
    assertThat(body.get("executors")).hasSize(2);
    assertThat(body.get("executors"))
        .extracting(executor -> executor.get("language").asText())
        .containsExactlyInAnyOrder("python", "javascript");
    assertThat(body.get("executors"))
        .allSatisfy(
            executor -> assertThat(executor.get("name").asText()).matches("(py|js)-[0-9a-f]{12}"));
    // Present and null: the local provider, like GCP, has no log group of its own.
    assertThat(body.get("executors"))
        .allSatisfy(executor -> assertThat(executor.get("logGroup").isNull()).isTrue());
  }

  @Test
  void isExposedByTheBundleOnItsManagementPort() throws IOException {
    final var properties = new Properties();
    try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
      properties.load(in);
    }

    assertThat(properties.getProperty("management.server.port")).isEqualTo("9080");
    assertThat(properties.getProperty("management.endpoints.web.exposure.include").split(","))
        .contains("managedcode");
  }

  private static Path temporaryDirectory() {
    try {
      return Files.createTempDirectory("managed-code-endpoint");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
