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

import io.camunda.common.auth.JwtConfig;
import io.camunda.common.auth.JwtCredential;
import io.camunda.common.auth.Product;
import io.camunda.common.auth.SaaSAuthenticationBuilder;
import io.camunda.common.json.JsonMapper;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.spring.client.properties.OperateClientConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SaaSOperateClientFactory {

  public static String SECRET_NAME_CLIENT_ID = "M2MClientId";
  public static String SECRET_NAME_SECRET = "M2MSecret";

  private final SecretProvider internalSecretProvider;
  private final OperateClientConfigurationProperties operateProperties;

  @Value("${camunda.operate.client.url}")
  private String operateUrl;

  public SaaSOperateClientFactory(
      @Autowired SaaSConfiguration saaSConfiguration,
      @Autowired OperateClientConfigurationProperties operateProperties) {
    this.internalSecretProvider = saaSConfiguration.getInternalSecretProvider();
    this.operateProperties = operateProperties;
  }

  @Bean
  @Primary
  public CamundaOperateClient camundaOperateClientBundle(JsonMapper jsonMapper) {

    var jwtConfig = new JwtConfig();
    jwtConfig.addProduct(Product.OPERATE, configureJwtCredential());

    var authentication =
        new SaaSAuthenticationBuilder().withJwtConfig(jwtConfig).withJsonMapper(jsonMapper).build();

    return CamundaOperateClient.builder()
        .operateUrl(operateUrl)
        .authentication(authentication)
        .setup()
        .build();
  }

  JwtCredential configureJwtCredential() {
    return new JwtCredential(
        internalSecretProvider.getSecret(SECRET_NAME_CLIENT_ID),
        internalSecretProvider.getSecret(SECRET_NAME_SECRET),
        operateProperties.getBaseUrl(),
        operateProperties.getAuthUrl());
  }
}
