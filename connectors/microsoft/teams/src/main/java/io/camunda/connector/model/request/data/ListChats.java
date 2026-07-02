/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.data;

import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.MSTeamsMethodTypes;

@TemplateSubType(
    id = MSTeamsMethodTypes.LIST_CHATS,
    label = "List chats",
    description = "List all chats available in Microsoft Teams",
    keywords = {"list chats", "chat list", "get chats", "fetch conversations", "browse chats"})
public record ListChats() implements ChatData {}
