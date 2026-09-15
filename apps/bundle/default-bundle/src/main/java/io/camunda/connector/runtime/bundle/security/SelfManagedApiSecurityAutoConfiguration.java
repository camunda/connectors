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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
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

/**
 * {@code POST /configurations/validate} resolves stored secrets, so self-managed requires an OIDC
 * token on it, allows an explicit unsecured mode, and otherwise answers 404.
 */
@Configuration
@EnableWebSecurity
@AutoConfigureBefore(ConnectorsAutoConfiguration.class)
@ConditionalOnMissingBean(name = SelfManagedApiSecurityAutoConfiguration.SAAS_CONSOLE_CHAIN)
public class SelfManagedApiSecurityAutoConfiguration {

  /** The SaaS Console JWT chain already covers this route; back off wherever it is registered. */
  static final String SAAS_CONSOLE_CHAIN = "connectorInstancesFilterChain";

  private static final String PROTECTED_ROUTES = "/configurations/**";

  @Value("${camunda.connector.auth.self-managed.issuer:}")
  private String issuer;

  @Value("${camunda.connector.auth.self-managed.audience:}")
  private String audience;

  @Value("${camunda.connector.configuration.validation.unsecured:false}")
  private boolean unsecured;

  /** First in the chain order, so no catch-all chain can claim the route ahead of it. */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain selfManagedConfigurationValidationFilterChain(HttpSecurity http)
      throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers(PROTECTED_ROUTES))
        .securityMatchers(matchers -> matchers.requestMatchers(PROTECTED_ROUTES))
        // Stateless, so a session from another route cannot satisfy authenticated() here.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    if (unsecured) {
      rejectAuthenticationConfiguration();
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    } else if (StringUtils.hasText(issuer)) {
      http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));
    } else {
      http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
          .exceptionHandling(
              exceptionHandling ->
                  exceptionHandling
                      .authenticationEntryPoint(
                          SelfManagedApiSecurityAutoConfiguration::respondNotFound)
                      .accessDeniedHandler(
                          SelfManagedApiSecurityAutoConfiguration::respondNotFound));
    }
    return http.build();
  }

  private void rejectAuthenticationConfiguration() {
    if (StringUtils.hasText(issuer) || StringUtils.hasText(audience)) {
      throw new IllegalStateException(
          "camunda.connector.configuration.validation.unsecured cannot be enabled together with "
              + "camunda.connector.auth.self-managed.issuer or "
              + "camunda.connector.auth.self-managed.audience");
    }
  }

  private JwtDecoder jwtDecoder() {
    if (!StringUtils.hasText(audience)) {
      throw new IllegalStateException(
          "camunda.connector.auth.self-managed.audience must be set when "
              + "camunda.connector.auth.self-managed.issuer is set. Without an audience, every "
              + "token the issuer signs for any of its clients would be accepted on "
              + PROTECTED_ROUTES
              + ". Set it to the aud claim of the tokens Camunda Hub forwards to this runtime.");
    }
    NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuer);
    jwtDecoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audience)));
    return jwtDecoder;
  }

  private static void respondNotFound(
      HttpServletRequest request, HttpServletResponse response, Exception ignoredException) {
    response.setStatus(HttpStatus.NOT_FOUND.value());
  }
}
