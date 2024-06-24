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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = {SaaSConnectorRuntimeApplication.class, MockSaaSConfiguration.class},
    properties = {
      "camunda.saas.secrets.projectId=42",
      "zeebe.client.cloud.cluster-id=42",
      "zeebe.client.security.plaintext=true",
      "camunda.connector.auth.audience=connectors.dev.ultrawombat.com",
      "camunda.connector.auth.issuer=https://weblogin.cloud.dev.ultrawombat.com/",
      "camunda.operate.client.url=" + MockSaaSConfiguration.OPERATE_CLIENT_URL,
      "camunda.operate.client.authUrl=" + MockSaaSConfiguration.OPERATE_CLIENT_AUTH_URL,
      "camunda.operate.client.baseUrl=" + MockSaaSConfiguration.OPERATE_CLIENT_BASEURL
    })
@ActiveProfiles("test")
public class TestSpringContextStartup {

  @Autowired private SaaSOperateClientFactory operateClientFactory;

  @Test
  public void contextLoaded() {
    // This test case just verifies that the runtime comes up without problems around
    // conflicting class files in logging or other wired behavior that can be observed
    // when the Spring context is initialized (e.g.
    // https://github.com/camunda/team-connectors/issues/251)
  }

  @Test
  public void jwtCredentialConfigured() {
    var jwtCredential = operateClientFactory.configureJwtCredential();
    assertThat(jwtCredential).isNotNull();
    assertThat(jwtCredential.getClientId())
        .isEqualTo(MockSaaSConfiguration.OPERATE_CLIENT_CLIENT_ID);
    assertThat(jwtCredential.getClientSecret())
        .isEqualTo(MockSaaSConfiguration.OPERATE_CLIENT_SECRET);
    assertThat(jwtCredential.getAudience()).isEqualTo(MockSaaSConfiguration.OPERATE_CLIENT_BASEURL);
    assertThat(jwtCredential.getAuthUrl()).isEqualTo(MockSaaSConfiguration.OPERATE_CLIENT_AUTH_URL);
  }
}
