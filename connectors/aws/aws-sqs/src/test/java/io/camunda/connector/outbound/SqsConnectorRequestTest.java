/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.model.MessageAttributeValue;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.outbound.model.SqsConnectorRequest;
import io.camunda.connector.outbound.model.SqsMessageAttribute;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqsConnectorRequestTest extends BaseTest {

  private SqsConnectorRequest request;
  private OutboundConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    context =
        OutboundConnectorContextBuilder.create()
            .validation(new DefaultValidationProvider())
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(SQS_QUEUE_REGION, ACTUAL_QUEUE_REGION)
            .secret(SQS_QUEUE_URL, ACTUAL_QUEUE_URL)
            .variables(DEFAULT_REQUEST_BODY_WITH_JSON_PAYLOAD)
            .build();
    request = context.bindVariables(SqsConnectorRequest.class);
  }

  @Test
  void execute_messageAttributesParsedCorrectly() {
    // Given request is DEFAULT_REQUEST_BODY with message attributes
    // When fetching native AWS SQS message attributes, they (native AWS SNS attributes) are mapped
    // correctly
    Map<String, SqsMessageAttribute> msgAttrsFromRequest =
        request.getQueue().getMessageAttributes();
    Map<String, MessageAttributeValue> msgAttrsRemapped =
        request.getQueue().getAwsSqsNativeMessageAttributes();

    assertThat(msgAttrsRemapped.size()).isNotZero();
    assertThat(msgAttrsRemapped).hasSameSizeAs(msgAttrsFromRequest);
  }
}
