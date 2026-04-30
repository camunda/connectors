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

import io.camunda.connector.test.utils.oidc.MockOidcServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {SaaSConnectorRuntimeApplication.class},
    properties = {
      "camunda.saas.secrets.projectId=42",
      "camunda.connector.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.connector.secretprovider.discovery.enabled=false",
      "camunda.client.auth.audience=connectors.dev.ultrawombat.com",
      "spring.cloud.gcp.parametermanager.enabled=false"
    })
// @ActiveProfiles("test")
// uncomment if you want readable logs
// we keep it disabled to test the setup e2e with stackdriver logging configuration as in prod
@ExtendWith(MockitoExtension.class)
public class TestSpringContextStartup {

  private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

  @DynamicPropertySource
  static void registerOidcProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.connector.auth.issuer", OIDC_SERVER::issuer);
    registry.add("camunda.client.auth.token-url", OIDC_SERVER::tokenUrl);
  }

  @AfterAll
  static void stopOidcServer() {
    OIDC_SERVER.close();
  }

  @MockitoBean(answers = Answers.RETURNS_MOCKS)
  public SaaSSecretConfiguration saaSSecretConfiguration;

  @Test
  public void contextLoaded() {
    // This test case just verifies that the runtime comes up without problems around
    // conflicting class files in logging or other wired behavior that can be observed
    // when the Spring context is initialized (e.g.
    // https://github.com/camunda/team-connectors/issues/251)
  }
}
