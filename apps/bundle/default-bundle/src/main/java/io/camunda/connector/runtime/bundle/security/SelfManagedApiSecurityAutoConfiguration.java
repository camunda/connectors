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
import io.camunda.connector.runtime.configuration.security.ConfigurationValidationDenyAllSecurityConfiguration;
import io.camunda.connector.runtime.configuration.security.ConfigurationValidationSecurityPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * The self-managed policy for {@code POST /configurations/validate}, which resolves stored secrets
 * to run a validator. It is this bundle's counterpart to the Console JWT chain {@code
 * camunda-saas-bundle} supplies for the same route.
 *
 * <p>Self-managed has no fixed identity provider the way SaaS has Console, so the policy is opt-in
 * via {@code camunda.connector.auth.self-managed.issuer} and {@code ...audience} (both required
 * together; startup fails on an issuer alone, which would accept every token that IdP signs for any
 * of its clients). Configure them to the issuer and {@code aud} of the tokens Hub forwards.
 *
 * <p>Absent the issuer this contributes nothing, and {@link
 * ConfigurationValidationDenyAllSecurityConfiguration} in the shared runtime keeps the route closed
 * — the operator simply doesn't get Hub's "validate credential" feature, the same degradation the
 * Hub adapter already applies to a {@code BASIC}-auth cluster or a too-old runtime.
 *
 * <p>Registered as an auto-configuration rather than component-scanned, because the runtime's
 * {@code @SpringBootApplication} lives in {@code connector-runtime-application} and scans its own
 * package. Ordered before {@link ConnectorsAutoConfiguration} so the {@link
 * ConfigurationValidationSecurityPolicy} below is in place when the shared fail-closed default
 * tests for it.
 *
 * <p>Also lands on the SaaS classpath, since {@code camunda-saas-bundle} depends on this bundle,
 * but contributes nothing there: SaaS does not set the self-managed issuer, and its own policy is
 * what stands the shared default down.
 */
@Configuration
@EnableWebSecurity
@AutoConfigureBefore(ConnectorsAutoConfiguration.class)
@ConditionalOnProperty(prefix = "camunda.connector.auth.self-managed", name = "issuer")
public class SelfManagedApiSecurityAutoConfiguration {

  @Value("${camunda.connector.auth.self-managed.issuer:}")
  private String issuer;

  @Value("${camunda.connector.auth.self-managed.audience:}")
  private String audience;

  @Bean
  public ConfigurationValidationSecurityPolicy selfManagedConfigurationValidationPolicy() {
    return new ConfigurationValidationSecurityPolicy("self-managed OIDC");
  }

  @Bean
  public SecurityFilterChain selfManagedConfigurationValidationFilterChain(HttpSecurity http)
      throws Exception {
    var routes = ConfigurationValidationDenyAllSecurityConfiguration.PROTECTED_ROUTES;
    http.csrf(csrf -> csrf.ignoringRequestMatchers(routes))
        .securityMatchers(matchers -> matchers.requestMatchers(routes))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(selfManagedJwtDecoder())));
    return http.build();
  }

  private JwtDecoder selfManagedJwtDecoder() {
    if (!StringUtils.hasText(audience)) {
      throw new IllegalStateException(
          "camunda.connector.auth.self-managed.audience must be set when "
              + "camunda.connector.auth.self-managed.issuer is set. Without an audience, every "
              + "token the issuer signs for any of its clients would be accepted on "
              + ConfigurationValidationDenyAllSecurityConfiguration.PROTECTED_ROUTES
              + ". Set it to the aud claim of the tokens Camunda Hub forwards to this runtime.");
    }
    NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuer);
    jwtDecoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audience)));
    return jwtDecoder;
  }
}
