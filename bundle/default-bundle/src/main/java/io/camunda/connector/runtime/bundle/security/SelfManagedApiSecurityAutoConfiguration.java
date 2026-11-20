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
package io.camunda.connector.runtime.bundle.security;

import io.camunda.connector.runtime.ConnectorsAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@AutoConfigureBefore(ConnectorsAutoConfiguration.class)
@ConditionalOnMissingBean(name = SelfManagedApiSecurityAutoConfiguration.SAAS_CONSOLE_CHAIN)
public class SelfManagedApiSecurityAutoConfiguration {

  static final String SAAS_CONSOLE_CHAIN = "inboundInstancesFilterChain";

  private static final String[] MANAGEMENT_ROUTES = {
    "/inbound", "/tenants/**", "/inbound-instances/**"
  };

  private static final String SECURE_MANAGEMENT_API_PROPERTY =
      "camunda.connector.auth.self-managed.secure-management-api";

  @Value("${camunda.connector.auth.self-managed.issuer:}")
  private String issuer;

  @Value("${camunda.connector.auth.self-managed.audience:}")
  private String audience;

  @Value("${" + SECURE_MANAGEMENT_API_PROPERTY + ":false}")
  private boolean secureManagementApi;

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 1)
  public SecurityFilterChain selfManagedManagementApiFilterChain(HttpSecurity http)
      throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers(MANAGEMENT_ROUTES))
        .securityMatchers(matchers -> matchers.requestMatchers(MANAGEMENT_ROUTES))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    if (secureManagementApi) {
      if (!StringUtils.hasText(issuer)) {
        throw new IllegalStateException(
            SECURE_MANAGEMENT_API_PROPERTY
                + " requires camunda.connector.auth.self-managed.issuer and "
                + "camunda.connector.auth.self-managed.audience to be set.");
      }
      http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(createJwtDecoder())));
    } else {
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    }
    return http.build();
  }

  private JwtDecoder createJwtDecoder() {
    if (!StringUtils.hasText(audience)) {
      throw new IllegalStateException(
          "camunda.connector.auth.self-managed.audience must be set when "
              + SECURE_MANAGEMENT_API_PROPERTY
              + " is enabled. Without an audience, every token the issuer signs for any of its "
              + "clients would be accepted. Set it to the aud claim of the tokens sent to this "
              + "runtime.");
    }
    NimbusJwtDecoder decoder = JwtDecoders.fromOidcIssuerLocation(issuer);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audience)));
    return decoder;
  }
}
