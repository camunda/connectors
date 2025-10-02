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
package io.camunda.connector.secret.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Objects;
import org.slf4j.Logger;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

public class AwsSecretProvider extends AbstractSecretProvider implements AutoCloseable {

  private final SecretsManagerClient secretsClient =
      SecretsManagerClient.builder()
          .region(new DefaultAwsRegionProviderChain().getRegion())
          .build();

  public AwsSecretProvider() {
    super();
  }

  public AwsSecretProvider(String clusterId, String secretsNamePrefix) {
    super(clusterId, null, secretsNamePrefix);
  }

  public AwsSecretProvider(ObjectMapper mapper, String clusterId, String secretsNamePrefix) {
    super(mapper, clusterId, null, secretsNamePrefix);
  }

  @Override
  protected String loadSecrets(
      String clusterId, String secretsProjectId, String secretsNamePrefix, Logger logger) {
    Objects.requireNonNull(clusterId, "You need to specify the clusterId to load secrets for");
    logger.info("Fetching secrets for cluster {} from aws secret manager", clusterId);

    try {
      final String secretName = String.format("%s-%s", secretsNamePrefix, clusterId);
      GetSecretValueRequest valueRequest =
          GetSecretValueRequest.builder().secretId(secretName).build();
      GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
      return valueResponse.secretString();
    } catch (final SecretsManagerException e) {
      logger.error("Error loading secret from aws: {}", e.awsErrorDetails().errorMessage());
      throw new ConnectorException(
          "Failed to load secret from AWS Secrets Manager: " + e.awsErrorDetails().errorMessage(),
          e);
    }
  }

  public void close() {
    if (secretsClient != null) {
      secretsClient.close();
    }
  }
}
