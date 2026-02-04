/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.designtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.designtime.ValueProviderContext;
import io.camunda.connector.slack.outbound.SlackRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SlackValueProviderTest {

  @Test
  void getOptions() throws Exception {
    String token = System.getenv("slack.token2");
    SlackValueProvider slackValueProvider = new SlackValueProvider();
    var configuration = Mockito.mock(ValueProviderContext.class);
    var slackRequestMock = mock(SlackRequest.class);
    when(slackRequestMock.token()).thenReturn(token);
    when(configuration.bindVariables(eq(SlackRequest.class))).thenReturn(slackRequestMock);
    var values = slackValueProvider.getOptions(configuration);
    Assertions.assertFalse(values.isEmpty());
  }
}
