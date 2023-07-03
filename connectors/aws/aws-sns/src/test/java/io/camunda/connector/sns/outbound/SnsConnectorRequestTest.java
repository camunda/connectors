/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.sns.outbound.model.SnsConnectorRequest;
import io.camunda.connector.sns.outbound.model.SnsMessageAttribute;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnsConnectorRequestTest extends BaseTest {

  private OutboundConnectorContext context;

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField() {
    // When request validate
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () ->
                OutboundConnectorContextBuilder.create()
                    .variables("{}")
                    .build()
                    .bindVariables(SnsConnectorRequest.class),
            "ConnectorInputException was expected");
    // Then we except exception with message
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input:");
  }

  @Test
  void replaceSecrets_shouldNotReplaceMessageBody() {
    // Given request with message body
    var context =
        OutboundConnectorContextBuilder.create()
            .variables("\"" + SECRETS + SNS_MESSAGE_BODY + "\"")
            .secret(SNS_MESSAGE_BODY, WRONG_MESSAGE_BODY)
            .build();
    // When replace secrets
    var request = context.bindVariables(String.class);
    // Then expect that message body will be same as was
    assertThat(request).isEqualTo(SECRETS + SNS_MESSAGE_BODY);
  }

  @Test
  void execute_messageAttributesParsedCorrectly() {
    // Given request is DEFAULT_REQUEST_BODY with message attributes
    // When fetching native AWS SNS message attributes, they (native AWS SNS attributes) are mapped
    // correctly
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(DEFAULT_REQUEST_BODY)
            .secret(SNS_MESSAGE_BODY, WRONG_MESSAGE_BODY)
            .build();
    var request = context.bindVariables(SnsConnectorRequest.class);
    Map<String, SnsMessageAttribute> msgAttrsFromRequest =
        request.getTopic().getMessageAttributes();
    Map<String, MessageAttributeValue> msgAttrsRemapped =
        request.getTopic().getAwsSnsNativeMessageAttributes();

    assertThat(msgAttrsRemapped.size()).isNotZero();
    assertThat(msgAttrsRemapped).hasSameSizeAs(msgAttrsFromRequest);
  }
}
