/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import io.camunda.connector.aws.model.AwsConfiguration;
import java.util.Optional;
import javax.validation.ValidationException;

public class AwsUtils {
  private AwsUtils() {}

  public static String extractRegionOrDefault(
      final AwsConfiguration configuration, final String region) {
    return Optional.ofNullable(configuration)
        .map(AwsConfiguration::getRegion)
        .or(() -> Optional.ofNullable(region))
        .filter(str -> !str.isBlank())
        .orElseThrow(
            () ->
                new ValidationException(
                    "Found constraints violated while validating input: - configuration.region: must not be empty"));
  }
}
