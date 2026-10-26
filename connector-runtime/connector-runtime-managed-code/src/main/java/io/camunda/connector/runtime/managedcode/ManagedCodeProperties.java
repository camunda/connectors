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
package io.camunda.connector.runtime.managedcode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the optional managed-script connector.
 *
 * @param deploymentNamePrefix prefix of every provider resource name, one per cluster; the identity
 *     of the runtime may only manage resources whose name starts with it
 * @param provisioningTimeout how long a job waits for the executors of the cluster before it fails
 *     without consuming a retry
 */
@ConfigurationProperties(prefix = "camunda.connector.managed-code")
public record ManagedCodeProperties(
    boolean enabled,
    String provider,
    String deploymentNamePrefix,
    Duration executionTimeout,
    Duration provisioningTimeout,
    Duration provisioningRetryBackoff,
    Local local,
    Aws aws,
    Gcp gcp) {

  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DEFAULT_PROVISIONING_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration DEFAULT_PROVISIONING_RETRY_BACKOFF = Duration.ofSeconds(10);

  /**
   * Cloud Run function ids have at most 63 characters, Lambda function names 64; the language and
   * wrapper digest take {@value ExecutorPackage#NAME_SUFFIX_LENGTH} of them.
   */
  private static final int MAX_DEPLOYMENT_NAME_PREFIX_LENGTH =
      63 - ExecutorPackage.NAME_SUFFIX_LENGTH;

  private static final Pattern DEPLOYMENT_NAME_PREFIX =
      Pattern.compile("[a-z][a-z0-9-]{0,%d}".formatted(MAX_DEPLOYMENT_NAME_PREFIX_LENGTH - 1));

  public ManagedCodeProperties {
    provider = provider == null ? "" : provider.strip();
    deploymentNamePrefix =
        deploymentNamePrefix == null || deploymentNamePrefix.isBlank()
            ? ExecutorPackage.DEFAULT_NAME_PREFIX
            : deploymentNamePrefix.strip();
    if (!DEPLOYMENT_NAME_PREFIX.matcher(deploymentNamePrefix).matches()) {
      throw new IllegalArgumentException(
          ("camunda.connector.managed-code.deployment-name-prefix '%s' must start with a lowercase"
                  + " letter and contain at most %d lowercase letters, digits and hyphens")
              .formatted(deploymentNamePrefix, MAX_DEPLOYMENT_NAME_PREFIX_LENGTH));
    }
    executionTimeout = positive(executionTimeout, DEFAULT_EXECUTION_TIMEOUT);
    provisioningTimeout = positive(provisioningTimeout, DEFAULT_PROVISIONING_TIMEOUT);
    provisioningRetryBackoff =
        positive(provisioningRetryBackoff, DEFAULT_PROVISIONING_RETRY_BACKOFF);
    local = local == null ? new Local(null) : local;
    aws = aws == null ? new Aws(null, null, 0, 0, null) : aws;
    gcp = gcp == null ? new Gcp(null, null, null, null, 0, 0, null) : gcp;
  }

  /**
   * Settings of the local provider.
   *
   * @param directory where the executor wrappers are written, and where each call gets a temporary
   *     directory
   */
  public record Local(Path directory) {
    public Local {
      directory =
          directory == null
              ? Path.of(System.getProperty("java.io.tmpdir"), "camunda-managed-code")
              : directory;
    }
  }

  /**
   * Settings of the AWS Lambda provider. Credentials come from the default AWS credentials chain.
   *
   * @param region region of the functions; the default region of the chain when absent
   * @param executionRoleArn role attached to the functions, granting nothing beyond logging
   * @param memoryMb memory of each executor
   * @param logRetentionDays retention of the log group created for each function, one of the values
   *     CloudWatch Logs accepts
   * @param pollInterval interval between status reads while an executor is created
   */
  public record Aws(
      String region,
      String executionRoleArn,
      int memoryMb,
      int logRetentionDays,
      Duration pollInterval) {

    private static final int DEFAULT_MEMORY_MB = 512;
    private static final int DEFAULT_LOG_RETENTION_DAYS = 14;
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(2);

    public Aws {
      region = region == null || region.isBlank() ? null : region.strip();
      executionRoleArn =
          executionRoleArn == null || executionRoleArn.isBlank() ? null : executionRoleArn.strip();
      memoryMb = memoryMb > 0 ? memoryMb : DEFAULT_MEMORY_MB;
      logRetentionDays = logRetentionDays > 0 ? logRetentionDays : DEFAULT_LOG_RETENTION_DAYS;
      pollInterval = positive(pollInterval, DEFAULT_POLL_INTERVAL);
    }
  }

  /**
   * Settings of the Google Cloud Run functions provider. Credentials come from the application
   * default credentials.
   *
   * @param project project of the functions
   * @param region region of the functions
   * @param functionServiceAccount service account the functions run as, granting nothing; required,
   *     since the platform would otherwise use the default Compute Engine service account
   * @param deployerServiceAccount service account the runtime impersonates to manage and invoke its
   *     functions; when absent, the application default credentials are used as they are and must
   *     be able to mint ID tokens
   * @param memoryMb memory of each executor
   * @param maxInstanceCount maximum instances of each executor, so the parallel scripts of the
   *     cluster per language; function code cannot change it
   * @param pollInterval interval between status reads while an executor is created
   */
  public record Gcp(
      String project,
      String region,
      String functionServiceAccount,
      String deployerServiceAccount,
      int memoryMb,
      int maxInstanceCount,
      Duration pollInterval) {

    private static final int DEFAULT_MEMORY_MB = 512;
    private static final int DEFAULT_MAX_INSTANCE_COUNT = 10;
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(5);

    public Gcp {
      project = blankToNull(project);
      region = blankToNull(region);
      functionServiceAccount = blankToNull(functionServiceAccount);
      deployerServiceAccount = blankToNull(deployerServiceAccount);
      memoryMb = memoryMb > 0 ? memoryMb : DEFAULT_MEMORY_MB;
      maxInstanceCount = maxInstanceCount > 0 ? maxInstanceCount : DEFAULT_MAX_INSTANCE_COUNT;
      pollInterval = positive(pollInterval, DEFAULT_POLL_INTERVAL);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
  }
}
