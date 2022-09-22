/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.io.IOException;

public interface SlackRequestData {
  void validateWith(final Validator validator);

  void replaceSecrets(final SecretStore secretStore);

  SlackResponse invoke(final MethodsClient methodsClient) throws SlackApiException, IOException;
}
