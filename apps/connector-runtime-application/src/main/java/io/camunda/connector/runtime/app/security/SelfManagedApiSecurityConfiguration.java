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
package io.camunda.connector.runtime.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Protects {@code POST /configurations/validate} on self-managed runtimes. It resolves stored
 * secrets to run a validator (see {@code ConfigurationValidationConfiguration}'s javadoc), and
 * unlike the SaaS bundle, self-managed has no security filter chain in front of it at all today —
 * anyone who can reach the runtime's HTTP port can name any secret it can resolve and have it
 * delivered to a URL of their choosing.
 *
 * <p>Self-managed has no fixed identity provider the way SaaS has Console, so protection is opt-in
 * via {@code camunda.connector.auth.self-managed.issuer} — but it fails <b>closed</b>: absent that
 * property, the route is denied outright rather than left open, the same way it silently is today.
 * An operator who wants Hub's "validate credential" feature configures this to the issuer their
 * cluster's Hub calls are already authenticated against (Hub forwards its caller's own bearer token
 * for a {@code BEARER_TOKEN}-auth cluster); everyone else simply doesn't get the feature — the same
 * degradation the Hub adapter already applies to a {@code BASIC}-auth cluster or a too-old runtime
 * (see {@code SelfManagedConnectorCredentialValidationAdapter#validate}).
 *
 * <p>Does not touch {@code /actuator/**}: that is addressed by giving self-managed its own {@code
 * management.server.port} (see {@code application.properties}), the same network-isolation approach
 * the SaaS bundle already uses, rather than authentication.
 *
 * <p>A custom Spring Boot application built directly on {@code spring-boot-starter-camunda-
 * connectors} (bypassing this module and {@code default-bundle} entirely) does not get this
 * protection and must add its own equivalent.
 */
@Configuration
@EnableWebSecurity
@Conditional(SelfManagedRuntimeCondition.class)
public class SelfManagedApiSecurityConfiguration {

  private static final String PROTECTED_ROUTES = "/configurations/**";

  @Value("${camunda.connector.auth.self-managed.issuer:}")
  private String issuer;

  @Value("${camunda.connector.auth.self-managed.audience:}")
  private String audience;

  @Bean
  @ConditionalOnProperty(prefix = "camunda.connector.auth.self-managed", name = "issuer")
  public SecurityFilterChain selfManagedConfigurationValidationFilterChain(HttpSecurity http)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .securityMatchers(matchers -> matchers.requestMatchers(PROTECTED_ROUTES))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(selfManagedJwtDecoder())));
    return http.build();
  }

  /**
   * Fail-closed default: with no issuer configured there is nothing safe to validate a caller's
   * token against, so the route is denied outright rather than left reachable. Answers with a plain
   * 404 rather than 401/403: the Hub adapter that calls this route ({@code
   * SelfManagedConnectorCredentialValidationAdapter}) already treats a 404 as "this runtime doesn't
   * support credential validation" and hides the feature accordingly (the same path a too-old
   * runtime takes); 401/403 would instead surface as a visible failure for every self-managed
   * operator who has not yet configured the issuer.
   */
  @Bean
  @ConditionalOnMissingBean(name = "selfManagedConfigurationValidationFilterChain")
  public SecurityFilterChain selfManagedConfigurationValidationDenyAllFilterChain(HttpSecurity http)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .securityMatchers(matchers -> matchers.requestMatchers(PROTECTED_ROUTES))
        .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(SelfManagedApiSecurityConfiguration::respondNotFound)
                    .accessDeniedHandler(SelfManagedApiSecurityConfiguration::respondNotFound));
    return http.build();
  }

  private static void respondNotFound(
      HttpServletRequest request, HttpServletResponse response, Exception ignoredException) {
    response.setStatus(HttpStatus.NOT_FOUND.value());
  }

  private JwtDecoder selfManagedJwtDecoder() {
    NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuer);
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
    jwtDecoder.setJwtValidator(
        StringUtils.hasText(audience)
            ? new DelegatingOAuth2TokenValidator<>(withIssuer, new AudienceValidator(audience))
            : withIssuer);
    return jwtDecoder;
  }
}
