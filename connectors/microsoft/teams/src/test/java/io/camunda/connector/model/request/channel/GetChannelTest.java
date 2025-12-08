/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import io.camunda.connector.BaseTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GetChannelTest extends BaseTest {

  @ParameterizedTest
  @MethodSource("getChannelValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input) {
    assertValidationException(input);
  }
}
