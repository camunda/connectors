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
package io.camunda.connector.runtime.saas;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.secret.SecretProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SaaSSecretConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(SaaSSecretConfiguration.class);

  @Test
  void whenLegacySecretsDisabled_secretProviderBeanIsNotCreated() {
    // camunda.saas.secrets.projectId is deliberately left unset here: with the central secret
    // store fallback in place, this is the whole point of the switch - the legacy GCP/AWS
    // provider must never be constructed, so a missing projectId must not crash the context.
    contextRunner
        .withPropertyValues(
            "camunda.saas.secrets.enabled=false", "camunda.client.cloud.clusterId=some-cluster")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(SecretProvider.class);
              assertThat(context.getBeanNamesForType(SecretProvider.class)).isEmpty();
            });
  }

  @Test
  void whenLegacySecretsEnabledButProjectIdMissing_contextFailsToStart() {
    // Documents the pre-fix crash this switch exists to avoid: with the flag left at its
    // default (enabled), the legacy provider bean still requires a project id.
    contextRunner
        .withPropertyValues("camunda.client.cloud.clusterId=some-cluster")
        .run(context -> assertThat(context).hasFailed());
  }
}
