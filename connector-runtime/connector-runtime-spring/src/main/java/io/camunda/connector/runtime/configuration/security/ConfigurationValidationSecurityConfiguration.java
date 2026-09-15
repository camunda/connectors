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
package io.camunda.connector.runtime.configuration.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Protects {@code POST /configurations/validate}, which resolves stored secrets. Opt in with {@code
 * camunda.connector.configuration.validation.security.enabled=true}.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
    prefix = "camunda.connector.configuration.validation.security",
    name = "enabled",
    havingValue = "true")
public class ConfigurationValidationSecurityConfiguration {

  private static final String ROUTE = "/configurations/**";

  @Value("${camunda.connector.auth.self-managed.issuer:}")
  private String issuer;

  @Value("${camunda.connector.auth.self-managed.audience:}")
  private String audience;

  /** Highest precedence, so no catch-all chain can claim the route first. */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain configurationValidationFilterChain(HttpSecurity http)
      throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers(ROUTE))
        .securityMatchers(matchers -> matchers.requestMatchers(ROUTE))
        // Stateless, so a session from another route cannot satisfy authenticated() here.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    if (StringUtils.hasText(issuer)) {
      http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));
    } else {
      http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
          .exceptionHandling(
              handling ->
                  handling
                      .authenticationEntryPoint(
                          ConfigurationValidationSecurityConfiguration::notFound)
                      .accessDeniedHandler(ConfigurationValidationSecurityConfiguration::notFound));
    }
    return http.build();
  }

  private JwtDecoder jwtDecoder() {
    if (!StringUtils.hasText(audience)) {
      throw new IllegalStateException(
          "camunda.connector.auth.self-managed.audience must be set alongside "
              + "camunda.connector.auth.self-managed.issuer. Without it, every token that issuer "
              + "signs for any of its clients would be accepted on "
              + ROUTE
              + ".");
    }
    NimbusJwtDecoder decoder = JwtDecoders.fromOidcIssuerLocation(issuer);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            // Jwt#getAudience is null, not empty, when the aud claim is absent.
            new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(audience))));
    return decoder;
  }

  private static void notFound(
      HttpServletRequest request, HttpServletResponse response, Exception ignored) {
    response.setStatus(HttpStatus.NOT_FOUND.value());
  }
}
