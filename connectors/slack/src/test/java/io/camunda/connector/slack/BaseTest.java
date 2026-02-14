/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

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
      String THREAD_NAME = "thread_ts";
      String CHANNEL_ID = "12345678";
      String TEXT = "_ this is secret test text _";
    }

    interface ConversationsCreateData {
      String NEW_CHANNEL_NAME = "_ new channel name _";
    }

    interface ReactionsAddData {
      String CHANNEL_ID = "C123ABC456";
      String EMOJI = "eyes";
      String TIMESTAMP = "1503435956.000247";
    }

    interface PinsAddData {
      String CHANNEL_ID = "C123ABC456";
      String TIMESTAMP = "1503435956.000247";
    }

    interface PinsRemoveData {
      String CHANNEL_ID = "C123ABC456";
      String TIMESTAMP = "1503435956.000247";
    }
  }

  protected interface SecretsConstant {
    String TOKEN = "TOKEN_KEY";

    interface ChatPostMessageData {
      String EMAIL = "EMAIL_KEY";
      String USERNAME = "USERNAME_KEY";
      String CHANNEL_NAME = "CHANNEL_NAME_KEY";
      String CHANNEL_ID = "CHANNEL_ID_KEY";
      String THREAD_NAME = "THREAD_NAME_KEY";
      String TEXT = "TEXT_KEY";
    }

    interface ConversationsCreateData {
      String NEW_CHANNEL_NAME = "NEW_CHANNEL_NAME_KEY";
    }

    interface ReactionsAddData {
      String CHANNEL_ID = "REACTION_CHANNEL_ID_KEY";
      String EMOJI = "REACTION_EMOJI_KEY";
      String TIMESTAMP = "REACTION_TIMESTAMP_KEY";
    }

    interface PinsAddData {
      String CHANNEL_ID = "PIN_CHANNEL_ID_KEY";
      String TIMESTAMP = "PIN_TIMESTAMP_KEY";
    }

    interface PinsRemoveData {
      String CHANNEL_ID = "UNPIN_CHANNEL_ID_KEY";
      String TIMESTAMP = "UNPIN_TIMESTAMP_KEY";
    }
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .validation(new DefaultValidationProvider())
        .secret(SecretsConstant.TOKEN, ActualValue.TOKEN)
        .secret(SecretsConstant.ChatPostMessageData.EMAIL, ActualValue.ChatPostMessageData.EMAIL)
        .secret(
            SecretsConstant.ChatPostMessageData.USERNAME, ActualValue.ChatPostMessageData.USERNAME)
        .secret(
            SecretsConstant.ChatPostMessageData.CHANNEL_NAME,
            ActualValue.ChatPostMessageData.CHANNEL_NAME)
        .secret(
            SecretsConstant.ChatPostMessageData.THREAD_NAME,
            ActualValue.ChatPostMessageData.THREAD_NAME)
        .secret(
            SecretsConstant.ChatPostMessageData.CHANNEL_ID,
            ActualValue.ChatPostMessageData.CHANNEL_ID)
        .secret(SecretsConstant.ChatPostMessageData.TEXT, ActualValue.ChatPostMessageData.TEXT)
        .secret(
            SecretsConstant.ConversationsCreateData.NEW_CHANNEL_NAME,
            ActualValue.ConversationsCreateData.NEW_CHANNEL_NAME)
        .secret(
            SecretsConstant.ReactionsAddData.CHANNEL_ID, ActualValue.ReactionsAddData.CHANNEL_ID)
        .secret(SecretsConstant.ReactionsAddData.EMOJI, ActualValue.ReactionsAddData.EMOJI)
        .secret(SecretsConstant.ReactionsAddData.TIMESTAMP, ActualValue.ReactionsAddData.TIMESTAMP)
        .secret(SecretsConstant.PinsAddData.CHANNEL_ID, ActualValue.PinsAddData.CHANNEL_ID)
        .secret(SecretsConstant.PinsAddData.TIMESTAMP, ActualValue.PinsAddData.TIMESTAMP)
        .secret(SecretsConstant.PinsRemoveData.CHANNEL_ID, ActualValue.PinsRemoveData.CHANNEL_ID)
        .secret(SecretsConstant.PinsRemoveData.TIMESTAMP, ActualValue.PinsRemoveData.TIMESTAMP);
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

  protected static Stream<String> executeAddReactionTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_ADD_REACTION);
  }

  protected static Stream<String> executePinMessageTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_PIN_MESSAGE);
  }

  protected static Stream<String> executeUnpinMessageTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.EXECUTE_UNPIN_MESSAGE);
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    var array = objectMapper.readValue(cases, ArrayList.class);
    return array.stream()
        .map(
            value -> {
              try {
                return objectMapper.writeValueAsString(value);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .map(Arguments::of);
  }
}
