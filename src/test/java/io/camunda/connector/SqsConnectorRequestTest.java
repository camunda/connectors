package io.camunda.connector;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.Validator;
import io.camunda.connector.model.SqsConnectorRequest;
import io.camunda.connector.test.ConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqsConnectorRequestTest extends BaseTest {

  private SqsConnectorRequest request;
  private Validator validator;
  private ConnectorContext context;

  @BeforeEach
  public void beforeEach() {
    request = new SqsConnectorRequest();
    validator = new Validator();

    context = ConnectorContextBuilder.create()
            .secret(SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(QUEUE_REGION_KEY, ACTUAL_QUEUE_REGION)
            .secret(QUEUE_URL_KEY, ACTUAL_QUEUE_URL)
            .build();

  }

  @Test
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField() {
    // Given request , where one field is null
    request.setAccessKey(ACCESS_KEY);
    request.setQueueRegion(ACTUAL_QUEUE_REGION);
    request.setQueueUrl(ACTUAL_QUEUE_URL);
    request.setSecretKey(SECRET_KEY);
    request.setMessageBody(null);
    // When request validate
    request.validateWith(validator);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.evaluate(),
            "IllegalArgumentException was expected");
    // Then we except exception with message
    assertEquals("Property 'message body' is missing", thrown.getMessage());
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceMessageBody() {
    // Given request with message body
    request.setMessageBody(SECRETS + SQS_MESSAGE_BODY);
    ConnectorContext context = ConnectorContextBuilder.create()
            .secret(SQS_MESSAGE_BODY, WRONG_MESSAGE_BODY)
            .build();
    // When replace secrets
    request.replaceSecrets(context.getSecretStore());
    // Then expect that message body will be same as was
    assertEquals(request.getMessageBody(), SECRETS + SQS_MESSAGE_BODY);
  }

  @Test
  void replaceSecrets_shouldReplaceSecrets() {
    // Given request with secrets. all secrets look like 'secrets.KEY'
    request.setSecretKey(SECRETS + SECRET_KEY);
    request.setAccessKey(SECRETS + ACCESS_KEY);
    request.setQueueUrl(SECRETS + QUEUE_URL_KEY);
    request.setQueueRegion(SECRETS + QUEUE_REGION_KEY);
    // When replace secrets
    request.replaceSecrets(context.getSecretStore());
    // Then
    assertEquals(request.getSecretKey(), ACTUAL_SECRET_KEY);
    assertEquals(request.getAccessKey(), ACTUAL_ACCESS_KEY);
    assertEquals(request.getQueueUrl(), ACTUAL_QUEUE_URL);
    assertEquals(request.getQueueRegion(), ACTUAL_QUEUE_REGION);
  }

  @Test
  void replaceSecrets_shouldDoNotReplaceSecretsIfTheyDidNotStartFromSecretsWord() {
    //Given request with data that not started from secrets. and context with secret store
    request.setSecretKey(SECRET_KEY);
    request.setAccessKey(ACCESS_KEY);
    request.setQueueUrl(QUEUE_URL_KEY);
    request.setQueueRegion(QUEUE_REGION_KEY);
    // When replace secrets
    request.replaceSecrets(context.getSecretStore());
    // Then secrets must be not replaced
    assertEquals(request.getSecretKey(), SECRET_KEY);
    assertEquals(request.getAccessKey(), ACCESS_KEY);
    assertEquals(request.getQueueUrl(), QUEUE_URL_KEY);
    assertEquals(request.getQueueRegion(), QUEUE_REGION_KEY);
  }

}
