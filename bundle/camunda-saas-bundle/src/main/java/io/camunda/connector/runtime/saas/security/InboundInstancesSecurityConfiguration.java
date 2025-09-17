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

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
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
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableWebSecurity
@Configuration
public class InboundInstancesSecurityConfiguration {

  @Value("${camunda.connector.auth.console.audience:}")
  private String consoleAudience;

  @Value("${camunda.connector.auth.allowed.roles:owner,admin,supportagent}")
  private List<String> allowedRoles;

  @Value("${camunda.connector.auth.issuer}")
  private String issuer;

  @Value("${camunda.endpoints.cors.allowed.origins:*}")
  private String[] allowedOrigins;

  @Value("${camunda.endpoints.cors.allow.credentials:false}")
  private boolean allowCredentials;

  @Value("${camunda.endpoints.cors.mappings:/**}")
  private List<String> mappings;

  @Value("${camunda.connector.cloud.organization.id:}")
  private String organizationId;

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        mappings.forEach(
            mapping ->
                registry
                    .addMapping(mapping)
                    .allowCredentials(allowCredentials)
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("*"));
      }
    };
  }

  @Bean
  @Order(2)
  public SecurityFilterChain inboundInstancesFilterChain(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.ignoringRequestMatchers("/inbound-instances/**"))
        .securityMatchers(
            requestMatcherConfigurer ->
                requestMatcherConfigurer.requestMatchers("/inbound-instances/**"))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers("/inbound-instances/**").authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.decoder(inboundInstancesJwtDecoder())));
    return http.build();
  }

  @Bean
  JwtDecoder inboundInstancesJwtDecoder() {
    NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuer);

    OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(consoleAudience);
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
    OAuth2TokenValidator<Jwt> withAudience =
        new DelegatingOAuth2TokenValidator<>(
            new OrganizationIdAndRolesValidator(organizationId, allowedRoles),
            withIssuer,
            audienceValidator);

    jwtDecoder.setJwtValidator(withAudience);
    return jwtDecoder;
  }
}
