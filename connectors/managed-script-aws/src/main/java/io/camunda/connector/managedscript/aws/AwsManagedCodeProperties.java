/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the {@code aws} managed-code provider. The prefix of the executor names and the
 * execution timeout are the shared ones of {@code camunda.connector.managed-code}. Credentials come
 * from the default chain of the AWS SDK only.
 *
 * @param region region of the executors; the SDK's default region chain when absent
 * @param executionRoleArn role the executors run with; it must grant nothing but writing to their
 *     log groups, since the script of a call can read the memory of the wrapper
 * @param memoryMb memory of an executor, in MB (default 512)
 * @param logRetentionDays retention of the log group of an executor, one of the values CloudWatch
 *     Logs accepts (default 30)
 */
@ConfigurationProperties(prefix = "camunda.connector.managed-code.aws")
public record AwsManagedCodeProperties(
    String region, String executionRoleArn, Integer memoryMb, Integer logRetentionDays) {

  private static final int DEFAULT_MEMORY_MB = 512;
  private static final int MIN_MEMORY_MB = 128;
  private static final int MAX_MEMORY_MB = 10_240;
  private static final int DEFAULT_LOG_RETENTION_DAYS = 30;

  /** The values {@code PutRetentionPolicy} accepts. */
  private static final Set<Integer> LOG_RETENTION_DAYS =
      Set.of(
          1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557,
          2922, 3288, 3653);

  public AwsManagedCodeProperties {
    region = region == null || region.isBlank() ? null : region.strip();
    if (executionRoleArn == null || executionRoleArn.isBlank()) {
      throw new IllegalArgumentException(
          "camunda.connector.managed-code.aws.execution-role-arn must be set for the 'aws'"
              + " managed-code provider");
    }
    executionRoleArn = executionRoleArn.strip();
    memoryMb = memoryMb == null ? DEFAULT_MEMORY_MB : memoryMb;
    if (memoryMb < MIN_MEMORY_MB || memoryMb > MAX_MEMORY_MB) {
      throw new IllegalArgumentException(
          "camunda.connector.managed-code.aws.memory-mb %d must be between %d and %d"
              .formatted(memoryMb, MIN_MEMORY_MB, MAX_MEMORY_MB));
    }
    logRetentionDays = logRetentionDays == null ? DEFAULT_LOG_RETENTION_DAYS : logRetentionDays;
    if (!LOG_RETENTION_DAYS.contains(logRetentionDays)) {
      throw new IllegalArgumentException(
          "camunda.connector.managed-code.aws.log-retention-days %d is not a retention CloudWatch"
                  .formatted(logRetentionDays)
              + " Logs accepts, such as 7, 14, 30 or 90");
    }
  }
}
