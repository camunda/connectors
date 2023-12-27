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

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class MockSaaSConfiguration {

  public static final String OPERATE_CLIENT_URL =
      "https://operate.cloud.dev.ultrawombat.com/myClusterId";
  public static final String OPERATE_CLIENT_BASEURL = "operate.cloud.dev.ultrawombat.com";
  public static final String OPERATE_CLIENT_AUTH_URL =
      "https://login.cloud.dev.ultrawombat.com/oauth/token";
  public static final String OPERATE_CLIENT_CLIENT_ID = "clientId";
  public static final String OPERATE_CLIENT_SECRET = "secret";

  private final Map<String, String> secrets =
      Map.of(
          SaaSOperateClientFactory.SECRET_NAME_CLIENT_ID, OPERATE_CLIENT_CLIENT_ID,
          SaaSOperateClientFactory.SECRET_NAME_SECRET, OPERATE_CLIENT_SECRET);

  @Bean
  public SaaSConfiguration saaSConfiguration(ZeebeClientConfigurationProperties conf) {
    return new SaaSConfiguration(conf) {
      @Override
      public SecretProvider getInternalSecretProvider() {
        return secrets::get;
      }
    };
  }
}
