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
package io.camunda.connector.managedscript.api;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * The settings under {@link #PREFIX} that the connector and every provider read, with their
 * defaults and validation, so that a provider does not depend on the connector.
 *
 * @param deploymentNamePrefix prefix of every executor name, one per cluster; the identity of the
 *     runtime may only manage executors whose name starts with it (default {@code camunda-ms-})
 * @param executionTimeout how long a script may run before the executor stops it (default 30s)
 */
public record ManagedCodeSettings(String deploymentNamePrefix, Duration executionTimeout) {

  public static final String PREFIX = "camunda.connector.managed-code";

  public static final String DEFAULT_DEPLOYMENT_NAME_PREFIX = "camunda-ms-";

  /** {@code <py|js>-} and the 12 hexadecimal characters of the wrapper digest. */
  public static final int EXECUTOR_NAME_SUFFIX_LENGTH = 15;

  /** Cloud Run function ids have at most 63 characters, Lambda function names 64. */
  public static final int MAX_DEPLOYMENT_NAME_PREFIX_LENGTH = 63 - EXECUTOR_NAME_SUFFIX_LENGTH;

  private static final Pattern DEPLOYMENT_NAME_PREFIX =
      Pattern.compile("[a-z][a-z0-9-]{0,%d}".formatted(MAX_DEPLOYMENT_NAME_PREFIX_LENGTH - 1));

  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofSeconds(30);

  public ManagedCodeSettings {
    deploymentNamePrefix =
        deploymentNamePrefix == null || deploymentNamePrefix.isBlank()
            ? DEFAULT_DEPLOYMENT_NAME_PREFIX
            : deploymentNamePrefix.strip();
    if (!DEPLOYMENT_NAME_PREFIX.matcher(deploymentNamePrefix).matches()) {
      throw new IllegalArgumentException(
          ("%s.deployment-name-prefix '%s' must start with a lowercase letter and contain at most"
                  + " %d lowercase letters, digits and hyphens")
              .formatted(PREFIX, deploymentNamePrefix, MAX_DEPLOYMENT_NAME_PREFIX_LENGTH));
    }
    executionTimeout =
        executionTimeout != null && !executionTimeout.isNegative() && !executionTimeout.isZero()
            ? executionTimeout
            : DEFAULT_EXECUTION_TIMEOUT;
  }
}
