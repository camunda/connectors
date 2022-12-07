/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class GsonSupplier {

  private static final SlackRequestDeserializer DESERIALIZER =
      new SlackRequestDeserializer("method")
              .registerType("chat.postMessage", ChatPostMessageData.class)
              .registerType("conversations.create", ConversationsCreateData.class)
              .registerType("conversations.invite", ConversationsInviteData.class);
  private static final Gson GSON =
      new GsonBuilder().registerTypeAdapter(SlackRequest.class, DESERIALIZER).create();

  private GsonSupplier() {}

  public static Gson getGson() {
    return GSON;
  }
}
