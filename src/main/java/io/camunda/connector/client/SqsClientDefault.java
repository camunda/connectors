package io.camunda.connector.client;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsClientDefault implements SqsClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqsClientDefault.class);

  private AmazonSQS client;
  private SendMessageRequest sendMessageRequest;

  @Override
  public void init(String accessKey, String secretKey, String region) {
    LOGGER.debug("Creating aws sqs client for queue region {}", region);
    client =
        AmazonSQSClientBuilder.standard()
            .withCredentials(
                new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
            .withRegion(region)
            .build();
    LOGGER.debug("Aws sqs client was successfully created");
  }

  @Override
  public void createMsg(String url, String msgData) {
    sendMessageRequest = new SendMessageRequest().withQueueUrl(url).withMessageBody(msgData);
    LOGGER.debug("Sqs message was successfully created for queue Url [{}]", url);
  }

  @Override
  public SendMessageResult execute() {
    final var sendMessageResult = client.sendMessage(sendMessageRequest);
    LOGGER.debug("sqs message was sent, msg id : [{}]", sendMessageResult.getMessageId());
    return sendMessageResult;
  }

  @Override
  public void shutDown() {
    if (client != null) {
      client.shutdown();
    }
    sendMessageRequest = null;
    client = null;
    LOGGER.debug("Sqs client was shutdown");
  }

  public AmazonSQS getClient() {
    return client;
  }

  public void setClient(AmazonSQS client) {
    this.client = client;
  }

  public SendMessageRequest getSendMessageRequest() {
    return sendMessageRequest;
  }

  public void setSendMessageRequest(SendMessageRequest sendMessageRequest) {
    this.sendMessageRequest = sendMessageRequest;
  }
}
