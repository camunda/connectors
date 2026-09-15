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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Fail-closed 404 on {@code /configurations/**} unless a module declares a policy. */
@Configuration
@EnableWebSecurity
public class ConfigurationValidationDenyAllSecurityConfiguration {

  public static final String PROTECTED_ROUTES = "/configurations/**";

  /** Shared by every chain on this route: Spring Security applies the first one that matches. */
  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

  @Bean
  @Order(ORDER)
  @ConditionalOnMissingBean(ConfigurationValidationSecurityPolicy.class)
  public SecurityFilterChain configurationValidationDenyAllFilterChain(HttpSecurity http)
      throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers(PROTECTED_ROUTES))
        .securityMatchers(matchers -> matchers.requestMatchers(PROTECTED_ROUTES))
        .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(
                        ConfigurationValidationDenyAllSecurityConfiguration::respondNotFound)
                    .accessDeniedHandler(
                        ConfigurationValidationDenyAllSecurityConfiguration::respondNotFound));
    return http.build();
  }

  private static void respondNotFound(
      HttpServletRequest request, HttpServletResponse response, Exception ignoredException) {
    response.setStatus(HttpStatus.NOT_FOUND.value());
  }
}
