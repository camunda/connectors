/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

interface TestCasesPath {
  String PATH = "src/test/resources/requests/";

  String PARSE_REQUEST = PATH + "parse-request-test-cases.json";

  interface Channel {
    String CHANNEL_PATH = PATH + "channel/";
    String SUCCESS_EXECUTE = PATH + "success-channel-request-test-cases.json";
    String CREATE_VALIDATION_FAIL = CHANNEL_PATH + "create-channel-validation-fail-test-cases.json";
    String GET_VALIDATION_FAIL = CHANNEL_PATH + "get-channel-validation-fail-test-cases.json";
    String GET_MESSAGE_VALIDATION_FAIL =
        CHANNEL_PATH + "get-channel-message-validation-fail-test-cases.json";
    String LIST_MEMBERS_VALIDATION_FAIL =
        CHANNEL_PATH + "list-channel-members-validation-fail-test-cases.json";
    String LIST_MESSAGES_VALIDATION_FAIL =
        CHANNEL_PATH + "list-channel-messages-validation-fail-test-cases.json";
    String LIST_VALIDATION_FAIL = CHANNEL_PATH + "list-channels-validation-fail-test-cases.json";
    String LIST_REPLIES_VALIDATION_FAIL =
        CHANNEL_PATH + "list-message-replies-validation-fail-test-cases.json";
    String SEND_MESSAGE_VALIDATION_FAIL =
        CHANNEL_PATH + "send-message-to-channel-validation-fail-test-cases.json";
  }

  interface Chat {
    String CHAT_PATH = PATH + "chat/";
    String SUCCESS_EXECUTE = PATH + "success-chat-request-test-cases.json";
    String GET_VALIDATION_FAIL = CHAT_PATH + "get-chat-validation-fail-test-cases.json";
    String CREATE_VALIDATION_FAIL = CHAT_PATH + "create-chat-validation-fail-test-cases.json";
    String GET_MESSAGE_VALIDATION_FAIL =
        CHAT_PATH + "get-message-in-chat-validation-fail-test-cases.json";
    String LIST_MEMBERS_VALIDATION_FAIL =
        CHAT_PATH + "list-chat-members-validation-fail-test-cases.json";
    String LIST_MESSAGES_VALIDATION_FAIL =
        CHAT_PATH + "list-messages-validation-fail-test-cases.json";
    String SEND_MESSAGE_VALIDATION_FAIL =
        CHAT_PATH + "send-message-validation-fail-test-cases.json";
  }
}
