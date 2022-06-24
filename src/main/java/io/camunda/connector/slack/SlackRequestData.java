package io.camunda.connector.slack;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.sdk.common.SecretStore;
import io.camunda.connector.sdk.common.Validator;

import java.io.IOException;

public interface SlackRequestData {
  void validate(final Validator validator);

  void replaceSecrets(final SecretStore secretStore);

  SlackResponse invoke(final MethodsClient methodsClient) throws SlackApiException, IOException;
}
