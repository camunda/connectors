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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Protects {@code POST /configurations/validate} on self-managed runtimes, which resolves stored
 * secrets to run a validator and today sits behind no filter chain at all — anyone who can reach
 * the runtime's HTTP port can name a secret and have it delivered to a URL of their choosing.
 *
 * <p>Self-managed has no fixed identity provider the way SaaS has Console, so the route is opt-in
 * via {@code camunda.connector.auth.self-managed.issuer} and {@code ...audience} (both required
 * together; startup fails on an issuer alone, which would accept every token that IdP signs for any
 * of its clients). Unconfigured, it fails closed as a 404 — the signal the calling Hub adapter
 * already reads as "this runtime is too old for credential validation", so it hides the feature
 * instead of reporting an error.
 *
 * <p>CSRF is exempted for this route, as {@code camunda-saas-bundle} also does for it: the caller
 * is a machine client with a bearer token and no session to replay.
 *
 * <p>Backs off when {@code camunda-saas-bundle}'s {@code ConnectorInstancesSecurityConfiguration}
 * is on the classpath, since it already covers this route and pulls this module in transitively.
 * The check is deliberately on that class rather than {@code camunda.client.mode}: a Hybrid runtime
 * is self-managed but sets that property to {@code saas} to reach a SaaS-hosted cluster, so keying
 * off it would disable this protection exactly where the SaaS classes are absent.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnMissingClass(
    "io.camunda.connector.runtime.saas.security.ConnectorInstancesSecurityConfiguration")
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
    http.csrf(csrf -> csrf.ignoringRequestMatchers(PROTECTED_ROUTES))
        .securityMatchers(matchers -> matchers.requestMatchers(PROTECTED_ROUTES))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(selfManagedJwtDecoder())));
    return http.build();
  }

  /** Fail-closed default: no issuer configured means nothing to validate against, so deny. */
  @Bean
  @ConditionalOnMissingBean(name = "selfManagedConfigurationValidationFilterChain")
  public SecurityFilterChain selfManagedConfigurationValidationDenyAllFilterChain(HttpSecurity http)
      throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers(PROTECTED_ROUTES))
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
}
