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

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.secret.providers.AbstractSecretProvider;
import io.camunda.connector.secret.providers.AwsSecretProvider;
import io.camunda.connector.secret.providers.GcpSecretProvider;
import java.util.Objects;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class SaaSSecretConfiguration {

  @Value("${camunda.saas.secrets.projectId:#{null}}")
  private String secretsProjectId;

  @Value("${camunda.saas.secrets.prefix:connector-secrets}")
  private String secretsNamePrefix;

  /** Internal prefix used for M2M tokens */
  @Value("${camunda.saas.secrets.internalPrefix:internal-connector-secrets}")
  private String secretsInternalNamePrefix;

  @Value("${camunda.client.cloud.clusterId}")
  private String clusterId;

  @Value("${camunda.saas.cluster.provider:gcp}")
  private String clusterProvider;

  private AbstractSecretProvider secretProvider;
  private AbstractSecretProvider internalSecretProvider;

  /**
   * Disabled via {@code camunda.saas.secrets.enabled=false} - defaulted to {@code false} in this
   * bundle's {@code application.properties} now that centralized secrets and the central-store
   * fallback (see {@code camunda.connector.secret-resolver.legacy.mode}) cover connector-secret
   * resolution. This is unrelated to Camunda client (M2M) authentication, which always keeps using
   * {@link #getInternalSecretProvider()} regardless of this switch - client authentication must
   * never be silently skipped. When disabled, this bean is never constructed, so {@code
   * camunda.saas.secrets.projectId} is not required unless a deployment still needs this legacy
   * connector-secrets provider (set the property to {@code true}).
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.saas.secrets",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public SecretProvider getSecretProvider() {
    if (Objects.equals(clusterProvider, "aws")) {
      secretProvider = new AwsSecretProvider(clusterId, secretsNamePrefix);
    } else {
      secretProvider = new GcpSecretProvider(clusterId, secretsProjectId, secretsNamePrefix);
    }
    return secretProvider;
  }

  public SecretProvider getInternalSecretProvider() {
    if (Objects.equals(clusterProvider, "aws")) {
      internalSecretProvider = new AwsSecretProvider(clusterId, secretsInternalNamePrefix);
    } else {
      internalSecretProvider =
          new GcpSecretProvider(clusterId, secretsProjectId, secretsInternalNamePrefix);
    }
    return internalSecretProvider;
  }

  @PreDestroy
  public void shutdown() throws Exception {
    if (internalSecretProvider != null) {
      internalSecretProvider.close();
    }
    if (secretProvider != null) {
      secretProvider.close();
    }
  }
}
