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
 * Protects {@code POST /configurations/validate} on self-managed runtimes. It resolves stored
 * secrets to run a validator (see {@code ConfigurationValidationConfiguration}'s javadoc), and
 * unlike the SaaS bundle, self-managed has no security filter chain in front of it at all today —
 * anyone who can reach the runtime's HTTP port can name any secret it can resolve and have it
 * delivered to a URL of their choosing.
 *
 * <p>Self-managed has no fixed identity provider the way SaaS has Console, so protection is opt-in
 * via {@code camunda.connector.auth.self-managed.issuer} — but it fails <b>closed</b>: absent that
 * property, the route is denied outright rather than left silently open the way it is today. An
 * operator who wants Hub's "validate credential" feature configures this to the issuer their
 * cluster's Hub calls are already authenticated against (Hub forwards its caller's own bearer token
 * for a {@code BEARER_TOKEN}-auth cluster); everyone else simply doesn't get the feature — the same
 * degradation the Hub adapter already applies to a {@code BASIC}-auth cluster or a too-old runtime
 * (see {@code SelfManagedConnectorCredentialValidationAdapter#validate}).
 *
 * <p>{@code camunda.connector.auth.self-managed.audience} is required whenever the issuer is set,
 * and startup fails if it is missing. An issuer on its own accepts every token that IdP signs for
 * any of its clients; on the org-wide IdP a typical self-managed install points at, that is a far
 * wider trust boundary than a route which resolves secrets warrants. Set it to the {@code aud}
 * claim of the tokens Hub forwards to this runtime.
 *
 * <p>CSRF is exempted for this route only, exactly as {@code camunda-saas-bundle} already exempts
 * it (and {@code /inbound-instances/**}, {@code /outbound/**}) in {@code
 * ConnectorInstancesSecurityConfiguration}: the caller is a server-side machine client presenting a
 * bearer token, with no cookie or session for a browser to replay, so a CSRF token would have
 * nothing to protect and would simply reject every legitimate Hub call.
 *
 * <p>Scope is this route only. {@code /actuator/**} also answers anonymously on self-managed, but
 * the fix there is network isolation (its own {@code management.server.port}, as {@code
 * camunda-saas-bundle} already does) rather than authentication, which would break unauthenticated
 * k8s probes. That move has to land together with a {@code camunda-platform-helm} change, since the
 * chart's Connectors probes currently target the public {@code http} port, so it is deliberately
 * left out of here.
 *
 * <p>A custom Spring Boot application built directly on {@code spring-boot-starter-camunda-
 * connectors} (bypassing this module and {@code default-bundle} entirely) does not get this
 * protection and must add its own equivalent.
 *
 * <p>Backs off when {@code camunda-saas-bundle}'s own {@code
 * ConnectorInstancesSecurityConfiguration} is on the classpath, since that module already covers
 * this route with the Console JWT chain and pulls this module in transitively at runtime. This is
 * deliberately a classpath check ({@code @ConditionalOnMissingClass}) rather than a check on {@code
 * camunda.client.mode}: in a Hybrid deployment, a self-managed runtime (running this module, not
 * {@code camunda-saas-bundle}) legitimately sets {@code camunda.client.mode=saas} to reach a
 * SaaS-hosted orchestration cluster. Keying off that property would have switched this protection
 * off precisely on that topology, while {@code camunda-saas-bundle}'s classes — the thing actually
 * being deferred to — are absent. Presence of its security class, unlike that property, is
 * decoupled from client-auth mode, so it is the correct signal for "is the SaaS bundle's own
 * protection actually here."
 *
 * <p>On why a self-managed policy lives here rather than in {@code default-bundle}, mirroring how
 * SaaS keeps its own in {@code camunda-saas-bundle}: this module is the self-managed application
 * ({@code ConnectorRuntimeApplication}), whereas {@code default-bundle} is a packaging module with
 * no main sources. Moving the class there would not buy the separation it looks like it should,
 * because {@code camunda-saas-bundle} depends on {@code connector-runtime-bundle} — the SaaS
 * classpath carries whatever the self-managed bundle carries, so the condition above stays
 * necessary either way, and a naive move would in fact invert the wiring (this module's component
 * scan is package-local, while the SaaS application scans all of {@code io.camunda.connector}).
 * What would actually remove the need for the condition is both bundles depending on a shared core
 * instead of SaaS depending on the self-managed bundle; that is a much larger change than the
 * placement of this class.
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
