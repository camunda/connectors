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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.auth.OperateJwtClientAssertionAuth;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.CamundaOperateClientConfiguration;
import io.camunda.operate.auth.Authentication;
import io.camunda.operate.spring.OperateClientConfiguration;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * Configuration for Camunda Operate client with JWT client assertion authentication support. This
 * configuration is activated when operate.client.jwt-assertion.enabled=true property is set,
 * enabling JWT client assertion authentication for self-managed Operate instances.
 */
@Configuration
public class OperateJwtClientAssertionConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(OperateJwtClientAssertionConfiguration.class);

  @Bean
  @Primary
  @ConditionalOnProperty(name = "operate.client.jwt-assertion.enabled", havingValue = "true")
  public CamundaOperateClient jwtClientAssertionOperateClient(
      ObjectMapper objectMapper, OperateClientConfiguration defaultConfiguration, Environment env) {

    String certPath = env.getProperty("OPERATE_SSL_CLIENT_CERT_PATH");
    String certPassword = env.getProperty("OPERATE_SSL_CLIENT_CERT_PASSWORD");
    String operateUrl = env.getProperty("operate.client.base-url");
    String clientId = env.getProperty("operate.client.client-id");
    String authUrl = env.getProperty("operate.client.auth-url");
    String audience = env.getProperty("operate.client.audience");
    String issuer = env.getProperty("OAUTH_ISSUER");

    if (certPath == null || operateUrl == null || clientId == null || authUrl == null) {
      throw new IllegalStateException(
          "Required environment variables missing for JWT client assertion: "
              + "OPERATE_SSL_CLIENT_CERT_PATH, operate.client.base-url, operate.client.client-id (or camunda.identity.client-id), operate.client.auth-url");
    }

    try {
      // Use the configured auth-url as the OAuth token endpoint
      String tokenEndpoint = authUrl;

      LOG.info("Configuring Operate client with JWT client assertion authentication");
      LOG.debug("Token endpoint: {}", tokenEndpoint);
      LOG.debug("Client ID: {}", clientId);
      LOG.debug("Audience: {}", audience);
      LOG.debug("Certificate path: {}", certPath);

      // Create JWT client assertion authentication provider
      OperateJwtClientAssertionAuth jwtAuth =
          new OperateJwtClientAssertionAuth(
              clientId, certPath, certPassword, tokenEndpoint, issuer, audience);

      // Create custom authentication that uses our JWT client assertion provider
      Authentication authentication =
          new Authentication() {
            @Override
            public void resetToken() {
              // Reset/invalidate cached tokens by calling resetToken on the JWT auth provider
              jwtAuth.resetToken();
            }

            @Override
            public Map<String, String> getTokenHeader() {
              String accessToken = jwtAuth.getAccessToken();
              Map<String, String> headers = new HashMap<>();
              headers.put("Authorization", "Bearer " + accessToken);
              return headers;
            }
          };
      URL operateUrlObj = new URI(removeTrailingSlash(operateUrl)).toURL();

      CamundaOperateClientConfiguration config =
          new CamundaOperateClientConfiguration(
              authentication,
              operateUrlObj,
              objectMapper,
              defaultConfiguration.operateHttpClient());

      LOG.info("Successfully configured Operate client with JWT client assertion authentication");
      return new CamundaOperateClient(config);

    } catch (Exception e) {
      LOG.error("Failed to configure Operate client with JWT client assertion", e);
      throw new RuntimeException("Failed to configure JWT client assertion for Operate client", e);
    }
  }

  private String removeTrailingSlash(String input) {
    if (input != null && input.endsWith("/")) {
      return input.substring(0, input.length() - 1);
    }
    return input;
  }
}
