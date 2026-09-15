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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.test.utils.oidc.MockOidcServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class ConfigurationValidationSecurityConfigurationTest {

  private static final MockOidcServer OIDC_SERVER = MockOidcServer.start();

  private static final String CHAIN = "configurationValidationFilterChain";
  private static final String ENABLED =
      "camunda.connector.configuration.validation.security.enabled=true";
  private static final String ISSUER = "camunda.connector.auth.self-managed.issuer=";
  private static final String AUDIENCE = "camunda.connector.auth.self-managed.audience=connectors";

  @AfterAll
  static void stopOidcServer() {
    OIDC_SERVER.close();
  }

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withUserConfiguration(ConfigurationValidationSecurityConfiguration.class);

  @Test
  void contributesNothingUnlessEnabled() {
    runner.run(context -> assertThat(context).doesNotHaveBean(CHAIN));
  }

  @Test
  void enabledWithoutIssuer_registersTheChain() {
    runner.withPropertyValues(ENABLED).run(context -> assertThat(context).hasBean(CHAIN));
  }

  @Test
  void enabledWithIssuerAndAudience_registersTheChain() {
    runner
        .withPropertyValues(ENABLED, ISSUER + OIDC_SERVER.issuer(), AUDIENCE)
        .run(context -> assertThat(context).hasBean(CHAIN));
  }

  @Test
  void enabledWithIssuerButNoAudience_failsToStart() {
    runner
        .withPropertyValues(ENABLED, ISSUER + OIDC_SERVER.issuer())
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("camunda.connector.auth.self-managed.audience"));
  }
}
