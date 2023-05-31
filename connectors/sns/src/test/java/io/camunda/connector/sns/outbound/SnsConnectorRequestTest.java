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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnsConnectorRequestTest extends BaseTest {

  private SnsConnectorRequest request;
  private OutboundConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = GSON.fromJson(DEFAULT_REQUEST_BODY, SnsConnectorRequest.class);

    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_TOPIC_ARN, ACTUAL_TOPIC_ARN)
            .secret(AWS_TOPIC_REGION, ACTUAL_TOPIC_REGION)
            .build();
  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField() {
    // Given request , where one field is null
    request.getTopic().setMessage(null);
    // When request validate
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.validate(request),
            "ConnectorInputException was expected");
    // Then we except exception with message
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input:");
  }

  @Test
  void replaceSecrets_shouldNotReplaceMessageBody() {
    // Given request with message body
    request.getTopic().setMessage(SECRETS + SNS_MESSAGE_BODY);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .secret(SNS_MESSAGE_BODY, WRONG_MESSAGE_BODY)
            .build();
    // When replace secrets
    context.replaceSecrets(request);
    // Then expect that message body will be same as was
    assertThat(request.getTopic().getMessage()).isEqualTo(SECRETS + SNS_MESSAGE_BODY);
  }

  @Test
  void replaceSecrets_shouldReplaceSecrets() {
    // Given request with secrets. all secrets look like 'secrets.KEY'
    request.getAuthentication().setAccessKey(SECRETS + AWS_ACCESS_KEY);
    request.getAuthentication().setSecretKey(SECRETS + AWS_SECRET_KEY);
    request.getTopic().setTopicArn(SECRETS + AWS_TOPIC_ARN);
    request.getTopic().setRegion(SECRETS + AWS_TOPIC_REGION);

    // When replace secrets
    context.replaceSecrets(request);
    // Then
    assertThat(request.getAuthentication().getSecretKey()).isEqualTo(ACTUAL_SECRET_KEY);
    assertThat(request.getAuthentication().getAccessKey()).isEqualTo(ACTUAL_ACCESS_KEY);
    assertThat(request.getTopic().getTopicArn()).isEqualTo(ACTUAL_TOPIC_ARN);
    assertThat(request.getTopic().getRegion()).isEqualTo(ACTUAL_TOPIC_REGION);
  }

  @Test
  void replaceSecrets_shouldNotReplaceSecretsIfTheyDidNotStartFromSecretsWord() {
    // Given request with data that not started from secrets. and context with secret store
    request.getAuthentication().setSecretKey(AWS_SECRET_KEY);
    request.getAuthentication().setAccessKey(AWS_ACCESS_KEY);
    request.getTopic().setTopicArn(AWS_TOPIC_ARN);
    request.getTopic().setRegion(AWS_TOPIC_REGION);

    // When replace secrets
    context.replaceSecrets(request);

    // Then secrets must be not replaced
    assertThat(request.getAuthentication().getSecretKey()).isEqualTo(AWS_SECRET_KEY);
    assertThat(request.getAuthentication().getAccessKey()).isEqualTo(AWS_ACCESS_KEY);
    assertThat(request.getTopic().getTopicArn()).isEqualTo(AWS_TOPIC_ARN);
    assertThat(request.getTopic().getRegion()).isEqualTo(AWS_TOPIC_REGION);
  }

  @Test
  void execute_messageAttributesParsedCorrectly() {
    // Given request is DEFAULT_REQUEST_BODY with message attributes
    // When fetching native AWS SNS message attributes, they (native AWS SNS attributes) are mapped
    // correctly
    Map<String, SnsMessageAttribute> msgAttrsFromRequest =
        request.getTopic().getMessageAttributes();
    Map<String, MessageAttributeValue> msgAttrsRemapped =
        request.getTopic().getAwsSnsNativeMessageAttributes();

    assertThat(msgAttrsRemapped.size()).isNotZero();
    assertThat(msgAttrsRemapped).hasSameSizeAs(msgAttrsFromRequest);
  }
}
