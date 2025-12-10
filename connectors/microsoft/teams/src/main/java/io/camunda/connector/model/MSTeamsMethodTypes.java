/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

public interface MSTeamsMethodTypes {
  String CREATE_CHANNEL = "createChannel";
  String GET_CHANNEL = "getChannel";
  String LIST_CHANNELS = "listAllChannels";
  String GET_CHANNEL_MESSAGE = "getMessageFromChannel";
  String SEND_MESSAGE_TO_CHANNEL = "sendMessageToChannel";
  String LIST_CHANNEL_MESSAGES = "listChannelMessages";
  String LIST_MESSAGE_REPLIES_IN_CHANNEL = "listMessageRepliesInChannel";
  String LIST_CHANNEL_MEMBERS = "listMembersInChannel";

  // Chat operations
  String CREATE_CHAT = "createChat";
  String GET_CHAT = "getChat";
  String LIST_CHATS = "listChats";
  String GET_MESSAGE_IN_CHAT = "getMessageFromChat";
  String LIST_CHAT_MEMBERS = "listMembersOfChat";
  String LIST_MESSAGES_IN_CHAT = "listMessagesInChat";
  String SEND_MESSAGE_IN_CHAT = "sendMessageToChat";
}
