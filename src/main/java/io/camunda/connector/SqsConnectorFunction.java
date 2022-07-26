package io.camunda.connector;

import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.Validator;
import io.camunda.connector.client.SqsClient;
import io.camunda.connector.client.SqsClientDefault;
import io.camunda.connector.model.SqsConnectorRequest;
import io.camunda.connector.model.SqsConnectorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsConnectorFunction implements ConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(SqsConnectorFunction.class);
  private static final Gson GSON = new GsonBuilder().create();
  private final SqsClient sqsClient;

  public SqsConnectorFunction() {
    this.sqsClient = new SqsClientDefault();
  }

  public SqsConnectorFunction(SqsClient sqsClient) {
    this.sqsClient = sqsClient;
  }

  @Override
  public Object execute(ConnectorContext context) {
    final var variables = context.getVariables();
    LOGGER.debug("Executing SQS connector with variables : {}", variables);
    final var request = GSON.fromJson(variables, SqsConnectorRequest.class);
    validate(request);
    request.replaceSecrets(context.getSecretStore());
    return new SqsConnectorResult(sendMsgToSqs(request).getMessageId());
  }

  private void validate(SqsConnectorRequest request) {
    final var validator = new Validator();
    request.validate(validator);
    validator.validate();
  }

  private SendMessageResult sendMsgToSqs(SqsConnectorRequest request) {
    try {
      sqsClient.init(request.getAccessKey(), request.getSecretKey(), request.getQueueRegion());
      sqsClient.createMsg(request.getQueueUrl(), request.getMessageBody());
      return sqsClient.execute();
    } finally {
      sqsClient.shutDown();
    }
  }
}
