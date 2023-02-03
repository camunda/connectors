/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.BaseTest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.model.MSTeamsRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ListChannelMembersTest extends BaseTest {

  @ParameterizedTest
  @MethodSource("listChannelMembersValidationFailTestCases")
  public void validate_shouldThrowExceptionWhenAtLeastOneRequiredFieldNotExist(String input)
      throws JsonProcessingException {
    // Given request without one required field
    MSTeamsRequest request = objectMapper.readValue(input, MSTeamsRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // When context.validate;
    // Then expect exception that one required field not set
    assertThat(request.getData()).isInstanceOf(ListChannelMembers.class);
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request.getData()),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
