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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lets a self-managed runtime enforce authentication on {@code /configurations/**} itself, instead
 * of relying on a path-aware rule in front of it.
 *
 * <p>{@code camunda.connector.configuration-validation.enabled} decides whether the route exists at
 * all (see {@code ConfigurationValidationConfiguration}). This configuration decides who may reach
 * it once it does, and it changes nothing while the route is off — the chain over {@code
 * /configurations/**} is declared only alongside the route itself, so a runtime that leaves
 * credential validation disabled keeps answering 404 there rather than starting to answer 403.
 * That distinction is load-bearing for the caller: Hub maps a 404 to {@code
 * ClusterResult.unsupported()} and its frontend hides that result, so a 404 is how "this cluster
 * does not offer credential validation" is expressed.
 *
 * <p>With the route enabled, the posture follows Spring Boot's standard {@code
 * spring.security.oauth2.resourceserver.jwt.issuer-uri}:
 *
 * <ul>
 *   <li><b>issuer configured</b> — a valid token from that issuer is required. This is the posture
 *       that matches a cluster registered in Hub as {@code BEARER_TOKEN}, where Hub attaches the
 *       calling user's own token to the request.
 *   <li><b>issuer absent</b> — the route is refused outright rather than served anonymously. This
 *       is the posture that matches a cluster registered in Hub as {@code NONE}, where Hub sends no
 *       credential at all; refusing is the point, since the alternative is resolving secrets for
 *       whoever asks.
 * </ul>
 *
 * <p>Both misconfigurations therefore fail closed: an issuer without the Hub registration, or the
 * Hub registration without an issuer, breaks credential validation rather than leaking through it.
 * A cluster registered as {@code BASIC} never reaches the runtime — Hub returns {@code unsupported}
 * for it before calling.
 *
 * <p><b>Authentication, not authorization.</b> A token accepted here proves the caller holds a
 * valid token from the cluster's issuer, not that they are entitled to read the secrets the route
 * resolves. Hub forwards the end user's own token, which was minted for Hub rather than for this
 * runtime, so an audience check cannot be a connectors audience; narrowing this further means
 * allow-listing the audience Hub's tokens actually carry, which is deliberately left out here
 * rather than guessed at.
 *
 * <p><b>Scope is deliberately narrow.</b> Everything else the runtime serves — {@code /inbound/**}
 * webhooks, {@code /inbound-instances/**}, {@code /outbound/**}, {@code /actuator/**} — keeps its
 * current anonymous behaviour via {@link #permitAllFilterChain}. Widening to the inbound/outbound
 * management routes is a separate change: those have an existing client (see {@code
 * InboundInstancesRestController}), so requiring a token there breaks it, which is not true of a
 * route that is off by default.
 *
 * <p>Backs off entirely in the SaaS bundle, which declares its own chains. The condition names the
 * SaaS class rather than testing for an existing {@link SecurityFilterChain} bean because
 * bean-based back-off between several chains resolves by {@link Order} and first-match-wins, which
 * fails silently in the wrong direction; the SaaS bundle transitively contains this module and
 * component scans {@code io.camunda.connector}, so without the condition it would contribute a
 * fourth chain.
 */
@EnableWebSecurity
@Configuration
@ConditionalOnMissingClass("io.camunda.connector.runtime.saas.security.SecurityConfiguration")
public class SelfManagedSecurityConfiguration {

  /**
   * Preserves the runtime's pre-existing anonymous posture for every route the credential
   * validation chain does not match. This is not a no-op: Spring Security is secure by default, so
   * without an explicit permit-all chain the mere presence of the library on the classpath would
   * reject unauthenticated webhook deliveries and Kubernetes probes.
   */
  @Bean
  @Order(100)
  public SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }

  /**
   * Declared only alongside the credential-validation route, so that disabling the route leaves the
   * runtime's 404 there intact — see the note on {@code ClusterResult.unsupported()} above.
   */
  @Configuration
  @ConditionalOnProperty(
      name = "camunda.connector.configuration-validation.enabled",
      havingValue = "true")
  static class CredentialValidationSecurityConfiguration {

    private static final Logger LOG =
        LoggerFactory.getLogger(CredentialValidationSecurityConfiguration.class);

    static final String CONFIGURATIONS_PATH = "/configurations/**";

    private static final String ISSUER_PROPERTY =
        "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    /**
     * Empty unless the deployment sets it; also what makes Boot contribute the {@code JwtDecoder}.
     */
    @Value("${" + ISSUER_PROPERTY + ":}")
    private String issuerUri = "";

    @Bean
    @Order(0)
    public SecurityFilterChain credentialValidationFilterChain(HttpSecurity http) throws Exception {
      http.securityMatchers(matchers -> matchers.requestMatchers(CONFIGURATIONS_PATH))
          // Called by a service, not a browser session; a CSRF token would only break it.
          .csrf(csrf -> csrf.disable());

      if (issuerUri.isBlank()) {
        LOG.warn(
            "Credential validation (POST /configurations/validate) is enabled but no {} is "
                + "configured, so the route is refused. The route resolves stored secrets and "
                + "presents them to a caller-named endpoint, so it is not served anonymously. Set "
                + "{} to the cluster's token issuer and register the cluster in Hub as "
                + "BEARER_TOKEN to enable it.",
            ISSUER_PROPERTY,
            ISSUER_PROPERTY);
        http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
      } else {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
      }
      return http.build();
    }
  }
}
