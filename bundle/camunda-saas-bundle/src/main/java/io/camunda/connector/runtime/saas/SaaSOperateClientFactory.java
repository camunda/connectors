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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.CamundaOperateClientConfiguration;
import io.camunda.operate.auth.JwtAuthentication;
import io.camunda.operate.auth.JwtCredential;
import io.camunda.operate.auth.TokenResponseMapper.JacksonTokenResponseMapper;
import io.camunda.operate.spring.OperateClientConfiguration;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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

  @Value("${camunda.operate.client.url}")
  private String operateUrl;

  @Value("${camunda.operate.client.baseUrl}")
  private String operateBaseUrl;

  @Value("${camunda.operate.client.authUrl}")
  private String operateAuthUrl;

  public SaaSOperateClientFactory(@Autowired SaaSConfiguration saaSConfiguration) {
    this.internalSecretProvider = saaSConfiguration.getInternalSecretProvider();
  }

  @Bean
  @Primary
  public CamundaOperateClient camundaOperateClientBundle(
      ObjectMapper objectMapper, OperateClientConfiguration configuration) {

    var jwtCredential = configureJwtCredential();
    var authentication =
        new JwtAuthentication(jwtCredential, new JacksonTokenResponseMapper(objectMapper));
    URL convertedOperateUrl;
    try {
      convertedOperateUrl = new URI(operateUrl).toURL();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    var adjustedConfiguration =
        new CamundaOperateClientConfiguration(
            authentication, convertedOperateUrl, objectMapper, configuration.operateHttpClient());
    return new CamundaOperateClient(adjustedConfiguration);
  }

  JwtCredential configureJwtCredential() {
    try {
      var authUrl = new URI(operateAuthUrl).toURL();
      return new JwtCredential(
          internalSecretProvider.getSecret(SECRET_NAME_CLIENT_ID),
          internalSecretProvider.getSecret(SECRET_NAME_SECRET),
          operateBaseUrl,
          authUrl);
    } catch (MalformedURLException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
