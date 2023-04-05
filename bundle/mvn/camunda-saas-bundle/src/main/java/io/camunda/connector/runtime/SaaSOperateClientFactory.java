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
package io.camunda.connector.runtime;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.auth.SaasAuthentication;
import io.camunda.operate.exception.OperateException;
import io.camunda.zeebe.spring.client.properties.OperateClientConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Primary
@Profile("!test")
@EnableConfigurationProperties(OperateClientConfigurationProperties.class)
public class SaaSOperateClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(SaaSOperateClientFactory.class);
  public static String SECRET_NAME_CLIENT_ID = "M2MClientId";
  public static String SECRET_NAME_SECRET = "M2MSecret";

  private final SecretProvider internalSecretProvider;

  public SaaSOperateClientFactory(@Autowired SaaSConfiguration saaSConfiguration) {
    this.internalSecretProvider = saaSConfiguration.getInternalSecretProvider();
  }

  @Bean
  @Primary
  public CamundaOperateClient camundaOperateClientBundle(
      OperateClientConfigurationProperties properties) throws OperateException {
    String operateClientId = internalSecretProvider.getSecret(SECRET_NAME_CLIENT_ID);
    String operateClientSecret = internalSecretProvider.getSecret(SECRET_NAME_SECRET);
    return new CamundaOperateClient.Builder()
        .operateUrl(properties.getOperateUrl())
        .authentication(
            new SaasAuthentication(
                properties.getAuthUrl(),
                properties.getAudience(),
                operateClientId,
                operateClientSecret))
        .build();
  }
}
