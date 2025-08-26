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
package io.camunda.connector.secret.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import java.util.Objects;
import org.slf4j.Logger;

public class GcpSecretProvider extends AbstractSecretProvider {

  public GcpSecretProvider() {
    super();
  }

  public GcpSecretProvider(String clusterId, String secretsProjectId, String secretsNamePrefix) {
    super(clusterId, secretsProjectId, secretsNamePrefix);
  }

  public GcpSecretProvider(
      ObjectMapper mapper, String clusterId, String secretsProjectId, String secretsNamePrefix) {
    super(mapper, clusterId, secretsProjectId, secretsNamePrefix);
  }

  protected String loadSecrets(
      String clusterId, String secretsProjectId, String secretsNamePrefix, Logger LOGGER) {
    Objects.requireNonNull(clusterId, "You need to specify the clusterId to load secrets for");
    LOGGER.info("Fetching secrets for cluster {} from gcp secret manager", clusterId);
    try (final SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
      final String secretName = String.format("%s-%s", secretsNamePrefix, clusterId);
      final SecretVersionName secretVersionName =
          SecretVersionName.of(secretsProjectId, secretName, "latest");
      final AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
      return response.getPayload().getData().toStringUtf8();
    } catch (final Exception e) {
      LOGGER.trace("Failed to load secrets from secret manager", e);
      throw new RuntimeException("Failed to load secrets from secret manager", e);
    }
  }
}
