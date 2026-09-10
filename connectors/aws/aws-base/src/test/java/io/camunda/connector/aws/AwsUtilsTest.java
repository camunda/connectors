/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

class AwsUtilsTest {

  @Test
  void blankConfigurationRegionFallsBackToLegacyRegion() {
    assertThat(AwsUtils.extractRegionOrDefault(new AwsBaseConfiguration(" ", null), "eu-central-1"))
        .isEqualTo("eu-central-1");
  }

  @Test
  void missingConfigurationAndFallbackRegionsAreRejected() {
    assertThatThrownBy(() -> AwsUtils.extractRegionOrDefault(null, null))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("configuration.region");
  }
}
