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
package io.camunda.connector.runtime.saas.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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

@EnableWebSecurity
@Configuration
public class SecurityConfiguration {

  @Value("${camunda.connector.auth.audience}")
  private String audience;

  @Value("${camunda.connector.auth.issuer}")
  private String issuer;

  /**
   * This is the first (spring priority order) filter chain. This is going to be applied first, if
   * nothing is matched, then the second one will be applied. Here, alls of the public endpoint are
   * caught first.
   *
   * @param http
   * @return The first security filter chain
   * @throws Exception
   */
  @Bean
  @Order(0)
  public SecurityFilterChain filterChain2(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.ignoringRequestMatchers("/inbound/**"))
        .securityMatchers(
            requestMatcherConfigurer ->
                requestMatcherConfigurer
                    .requestMatchers(HttpMethod.GET, "/inbound/*")
                    .requestMatchers(HttpMethod.POST, "/inbound/*")
                    .requestMatchers(HttpMethod.PUT, "/inbound/*")
                    .requestMatchers(HttpMethod.DELETE, "/inbound/*")
                    .requestMatchers("actuator/**"))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }

  /**
   * This is the second (spring priority order) filter chain. This has to be applied in second cause
   * spring-security is secure by default and therefore will protect every endpoint with Oauth2 If
   * this was first and endpoint like `GET /inbound/*` would respond 401 Those endpoint will be
   * caught on the first security chain
   *
   * @param http
   * @return The second security filter chain
   * @throws Exception
   */
  @Bean
  @Order(1)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/inbound/**"))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/inbound", "/tenants/**")
                    .hasAuthority("SCOPE_inbound:read"))
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));
    return http.build();
  }

  @Bean
  JwtDecoder jwtDecoder() {
    NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuer);

    OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
    OAuth2TokenValidator<Jwt> withAudience =
        new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

    jwtDecoder.setJwtValidator(withAudience);
    return jwtDecoder;
  }
}
