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
package io.camunda.connector.runtime.saas.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication;
import io.camunda.connector.runtime.saas.SaaSSecretConfiguration;
import io.camunda.connector.test.utils.oidc.MockOidcServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Reproduces issue 1 from https://github.com/camunda/connectors/issues/8001: a deployment that only
 * configures a named client's auth properties (`camunda.clients.<name>.auth.*`) and never sets the
 * global `camunda.client.auth.*` equivalents must not crash on startup, even though {@link
 * io.camunda.connector.runtime.saas.CamundaClientSaaSConfiguration} injects those global values as
 * a fallback for the internal-secret-manager path.
 */
@SpringBootTest(
    classes = {SaaSConnectorRuntimeApplication.class},
    properties = {
      "camunda.saas.secrets.projectId=42",
      "camunda.connector.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.connector.secretprovider.discovery.enabled=false",
      "camunda.clients.default.auth.audience=connectors.dev.ultrawombat.com",
      // NOTE: neither camunda.client.auth.token-url/audience (global) nor client-id/client-secret
      // are provided - only the per-client auth properties below are set.
      "spring.cloud.gcp.parametermanager.enabled=false"
    })
@ActiveProfiles("test")
public class PerClientOnlyAuthConfigBootTest {

  private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

  @DynamicPropertySource
  static void registerOidcProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.connector.auth.issuer", OIDC_SERVER::issuer);
    registry.add("camunda.clients.default.auth.token-url", OIDC_SERVER::tokenUrl);
  }

  @AfterAll
  static void stopOidcServer() {
    OIDC_SERVER.close();
  }

  @MockitoBean(answers = Answers.RETURNS_MOCKS)
  public SaaSSecretConfiguration saaSSecretConfiguration;

  @Autowired private ApplicationContext applicationContext;

  @Test
  public void contextStartsWithOnlyPerClientAuthPropertiesConfigured() {
    assertThat(applicationContext).isNotNull();
  }
}
