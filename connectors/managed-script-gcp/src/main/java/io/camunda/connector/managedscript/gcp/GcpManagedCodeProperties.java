/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the {@code gcp} managed-code provider. The prefix of the executor names and the
 * execution timeout are the shared ones of {@code camunda.connector.managed-code}. Credentials come
 * from the application default credentials only.
 *
 * @param projectId project of the executors
 * @param region region of the executors, such as {@code europe-west1}
 * @param functionServiceAccount email of the service account the executors run as; it must hold no
 *     role, since the script of a call can read the memory of the wrapper. Required: without it,
 *     Cloud Run would use the default compute service account, which often has Editor on the
 *     project
 * @param deployerServiceAccount email of a service account that the application default credentials
 *     impersonate to create and call the executors; none to use them as they are
 * @param memory memory of an executor, in {@code Mi} or {@code Gi} (default {@code 512Mi})
 * @param maxInstanceCount most instances of an executor, hence most scripts of a language running
 *     at once (default 1000, the most Cloud Run allows without a quota increase)
 */
@ConfigurationProperties(prefix = "camunda.connector.managed-code.gcp")
public record GcpManagedCodeProperties(
    String projectId,
    String region,
    String functionServiceAccount,
    String deployerServiceAccount,
    String memory,
    Integer maxInstanceCount) {

  private static final String DEFAULT_MEMORY = "512Mi";
  private static final int DEFAULT_MAX_INSTANCE_COUNT = 1000;
  private static final long MIB = 1024L * 1024;
  private static final long MIN_MEMORY_BYTES = 128 * MIB;
  private static final long MAX_MEMORY_BYTES = 32 * 1024 * MIB;
  private static final Pattern MEMORY = Pattern.compile("[1-9][0-9]{0,5}(Mi|Gi)");

  /** What the Cloud Functions API may answer for a memory: a quantity with a binary or SI unit. */
  private static final Pattern QUANTITY = Pattern.compile("([0-9]+)(k|Ki|M|Mi|G|Gi)?");

  public GcpManagedCodeProperties {
    projectId = required(projectId, "project-id");
    region = required(region, "region");
    functionServiceAccount = required(functionServiceAccount, "function-service-account");
    if (!functionServiceAccount.contains("@")) {
      throw new IllegalArgumentException(
          "camunda.connector.managed-code.gcp.function-service-account '%s' must be the email of a"
                  .formatted(functionServiceAccount)
              + " service account");
    }
    deployerServiceAccount =
        deployerServiceAccount == null || deployerServiceAccount.isBlank()
            ? null
            : deployerServiceAccount.strip();
    memory = memory == null || memory.isBlank() ? DEFAULT_MEMORY : memory.strip();
    if (!MEMORY.matcher(memory).matches()
        || memoryBytes(memory) < MIN_MEMORY_BYTES
        || memoryBytes(memory) > MAX_MEMORY_BYTES) {
      throw new IllegalArgumentException(
          ("camunda.connector.managed-code.gcp.memory '%s' must be between 128Mi and 32Gi, in Mi or"
                  + " Gi, such as 512Mi or 1Gi")
              .formatted(memory));
    }
    maxInstanceCount = maxInstanceCount == null ? DEFAULT_MAX_INSTANCE_COUNT : maxInstanceCount;
    if (maxInstanceCount < 1) {
      throw new IllegalArgumentException(
          "camunda.connector.managed-code.gcp.max-instance-count %d must be positive"
              .formatted(maxInstanceCount));
    }
  }

  /** {@code projects/<project>/locations/<region>}. */
  String location() {
    return "projects/%s/locations/%s".formatted(projectId, region);
  }

  /**
   * The bytes of a memory quantity, such as {@code 512Mi} or {@code 1G}, or -1 when it is not one.
   */
  static long memoryBytes(String quantity) {
    final var matcher = QUANTITY.matcher(quantity == null ? "" : quantity);
    if (!matcher.matches()) {
      return -1;
    }
    final long unit =
        switch (matcher.group(2) == null ? "" : matcher.group(2)) {
          case "k" -> 1000L;
          case "Ki" -> 1024L;
          case "M" -> 1000L * 1000;
          case "Mi" -> MIB;
          case "G" -> 1000L * 1000 * 1000;
          case "Gi" -> 1024 * MIB;
          default -> 1L;
        };
    try {
      return Math.multiplyExact(Long.parseLong(matcher.group(1)), unit);
    } catch (ArithmeticException | NumberFormatException e) {
      return -1;
    }
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "camunda.connector.managed-code.gcp.%s must be set for the 'gcp' managed-code provider"
              .formatted(name));
    }
    return value.strip();
  }
}
