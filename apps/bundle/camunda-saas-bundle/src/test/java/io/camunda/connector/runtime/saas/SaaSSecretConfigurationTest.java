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
  void whenSecretsDisabled_secretProviderBeanIsNotCreated() {
    // camunda.saas.secrets.projectId is deliberately left unset here: with the legacy
    // connector-secrets provider disabled, this is the whole point of the switch - it must never
    // be constructed, so a missing projectId must not crash the context. This is exactly the
    // crash reported when centralized secrets + the central-store fallback were enabled on dev.
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
  void whenSecretsEnabledButProjectIdMissing_contextFailsToStart() {
    // Documents the pre-fix crash this switch exists to avoid: with the flag explicitly turned
    // back on (a deployment that still needs this legacy provider), it still requires a project
    // id.
    contextRunner
        .withPropertyValues(
            "camunda.saas.secrets.enabled=true", "camunda.client.cloud.clusterId=some-cluster")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void isUnaffectedByLegacySecretResolverMode() {
    // This switch is independent of camunda.connector.secret-resolver.legacy.mode, which governs
    // {{secrets.X}} resolution, not whether this bundle's own GCP/AWS provider gets built.
    contextRunner
        .withPropertyValues(
            "camunda.saas.secrets.enabled=false",
            "camunda.connector.secret-resolver.legacy.mode=ON",
            "camunda.client.cloud.clusterId=some-cluster")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(SecretProvider.class);
            });
  }
}
