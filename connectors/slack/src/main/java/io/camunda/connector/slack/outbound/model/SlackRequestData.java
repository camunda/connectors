/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.model;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.slack.outbound.SlackResponse;
import java.io.IOException;

@TemplateDiscriminatorProperty(
    label = "Method",
    group = "method",
    name = "method",
    defaultValue = "chat.postMessage")
@TemplateSubType(id = "method", label = "Method")
public sealed interface SlackRequestData
    permits ChatPostMessageData, ConversationsCreateData, ConversationsInviteData {

  SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException;
}
