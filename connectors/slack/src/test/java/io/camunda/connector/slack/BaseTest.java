/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.gson.Gson;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.slack.suppliers.GsonSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  protected static final Gson gson = GsonSupplier.getGson();
  protected static OutboundConnectorContext context;

  protected interface ActualValue {
    String USER_ID = "1234567890123";
    String USER_REAL_NAME = "JohnDou";
    String TS = "ts";
    String TOKEN = "xoxb-0123456789456-123467890987-thisIsTestToken";
    String METHOD = "chat.postMessage";

    interface ChatPostMessageData {
      String EMAIL = "john.dou@camundamail.com";
      String USERNAME = "@" + USER_REAL_NAME;
      String CHANNEL_NAME = "#john.channel";
      String CHANNEL_ID = "12345678";
      String TEXT = "_ this is secret test text _";
    }

    interface ConversationsCreateData {
      String NEW_CHANNEL_NAME = "_ new channel name _";
    }
  }

  protected interface SecretsConstant {
    String TOKEN = "TOKEN_KEY";

    interface ChatPostMessageData {
      String EMAIL = "EMAIL_KEY";
      String USERNAME = "USERNAME_KEY";
      String CHANNEL_NAME = "CHANNEL_NAME_KEY";
      String CHANNEL_ID = "CHANNEL_ID_KEY";
      String TEXT = "TEXT_KEY";
    }

    interface ConversationsCreateData {
      String NEW_CHANNEL_NAME = "NEW_CHANNEL_NAME_KEY";
    }
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.TOKEN, ActualValue.TOKEN)
        .secret(SecretsConstant.ChatPostMessageData.EMAIL, ActualValue.ChatPostMessageData.EMAIL)
        .secret(
            SecretsConstant.ChatPostMessageData.USERNAME, ActualValue.ChatPostMessageData.USERNAME)
        .secret(
            SecretsConstant.ChatPostMessageData.CHANNEL_NAME,
            ActualValue.ChatPostMessageData.CHANNEL_NAME)
        .secret(
            SecretsConstant.ChatPostMessageData.CHANNEL_ID,
            ActualValue.ChatPostMessageData.CHANNEL_ID)
        .secret(SecretsConstant.ChatPostMessageData.TEXT, ActualValue.ChatPostMessageData.TEXT)
        .secret(
            SecretsConstant.ConversationsCreateData.NEW_CHANNEL_NAME,
            ActualValue.ConversationsCreateData.NEW_CHANNEL_NAME);
  }

  protected static Stream<String> replaceSecretsSuccessTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.REPLACE_SECRETS);
  }

  protected static Stream<String> validateRequiredFieldsFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.VALIDATE_REQUIRED_FIELDS_FAIL);
  }

  protected static Stream<String> executeWithEmailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_WITH_EMAIL);
  }

  protected static Stream<String> executeWithUserNameTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_WITH_USERNAME);
  }

  protected static Stream<String> executeWithChannelNameTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_WITH_CHANNEL_NAME);
  }

  protected static Stream<String> executeCreateChannelTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_CREATE_CHANNEL);
  }

  protected static Stream<String> executeInviteToChannelTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_INVITE_TO_CHANNEL);
  }

  protected static Stream<String> executeInviteToChannelTestCasesWrongInput() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_INVITE_TO_CHANNEL_WRONG_INPUT);
  }

  protected static Stream<String> fromJsonFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.FROM_JSON_FAIL);
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final Gson testingGson = new Gson();
    var array = testingGson.fromJson(cases, ArrayList.class);
    return array.stream().map(testingGson::toJson).map(Arguments::of);
  }
}
