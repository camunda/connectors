package io.camunda.connector.client;

import com.amazonaws.services.sqs.model.SendMessageResult;

public interface SqsClient {

  void init(String accessKey, String secretKey, String region);

  void createMsg(String url, String msgData);

  SendMessageResult execute();

  void shutDown();
}
