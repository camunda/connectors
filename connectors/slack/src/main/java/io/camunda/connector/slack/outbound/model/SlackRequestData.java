/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.slack.outbound.SlackResponse;
import java.io.IOException;

public sealed interface SlackRequestData
    permits ChatPostMessageData, ConversationsCreateData, ConversationsInviteData {

  SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException;
}
