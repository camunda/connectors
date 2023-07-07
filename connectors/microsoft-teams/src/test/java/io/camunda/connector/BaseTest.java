/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.model.MSTeamsRequest;
import io.camunda.connector.suppliers.ObjectMapperSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  protected static final ObjectMapper objectMapper = ObjectMapperSupplier.objectMapper();

  protected interface ActualValue {

    interface Authentication {
      String CLIENT_ID = "abc01234-0c7f-012c-9876-testClientId";
      String TENANT_ID = "5357c0a8-4728-418b-8c41-testTenantId";
      String CLIENT_SECRET = "Wao8Q~yzXYfgdM_testClientSecret_RZyqPaET";

      String BEARER_TOKEN = "BEARER_TOKEN_Wao8Q~yzXYfgdM_RZyqPaET01696d2e4a179c292bc9cf04e63b";
      String REFRESH_TOKEN =
          "REFRESH_TOKEN_AgABAAEAAAD--DLA3VO7QrddgJg7WevrAgDs_wQA9P-P7wgV-lUKIE_JfYDJK912TTwhqJD7WmYcHfnNtlLgC-i9bG8_vmSYs1GLIYe4KnZ4KTOxNqh74kjnrwLdyuMnUrOYXtBBnT-p8RCqW8GefJpIM0mAJ7HVtD4ghBfFzrQBeS2QuYOvh_dcIQ9nET01696d2e4a179c292bc9cf04e63b";
    }

    interface Chat {
      String CHAT_ID = "19:1c5b01696d2e4a179c292bc9cf04e63b@thread.v2";
      String MESSAGE_ID = "01223456789";
      String FILTER =
          "lastModifiedDateTime ge 2022-09-22T00:00:00.000Z and lastModifiedDateTime le 2022-09-24T00:00:00.000Z";
      String CONTENT_PART_1 = "Hi microsoft teams ";
      String CONTENT_PART_2 = "from camunda connector";
    }

    interface Channel {
      String CHANNEL_ID = "abc01234-0c7f-012c-9876-testClientId";
      String GROUP_ID = "19:1c5b01696d2e4a179c292bc9cf04e63b@thread.v2";
      String NAME = "ChannelTest";
      String MESSAGE_ID = "01234436675734";
      String DESCRIPTION = "Test channel description";
      String CHANNEL_TYPE_STANDARD = "standard";
      String OWNER = "john.dou@mail.com";
      String FILTER = "createdDateTime desc";
      String CONTENT = "Hi Microsoft Teams channel from camunda!!!";
      String TOP = "49";
    }
  }

  interface Secrets {

    interface Authentication {
      String CLIENT_ID = "CLIENT_ID_KEY";
      String TENANT_ID = "TENANT_ID_KEY";
      String CLIENT_SECRET = "CLIENT_SECRET_KEY";
      String BEARER_TOKEN = "BEARER_TOKEN_KEY";
      String REFRESH_TOKEN = "REFRESH_TOKEN_KEY";
    }

    interface Chat {
      String CHAT_ID = "CHAT_ID_KEY";
      String MESSAGE_ID = "MESSAGE_ID_KEY";
      String FILTER = "FILTER_KEY";
      String CONTENT_PART_1 = "CONTENT_PART_1_KEY";
      String CONTENT_PART_2 = "CONTENT_PART_2_KEY";
    }

    interface Channel {
      String CHANNEL_ID = "CHANNEL_ID_KEY";
      String GROUP_ID = "GROUP_ID_KEY";
      String NAME = "CHANNEL_NAME_KEY";
      String MESSAGE_ID = "CHANNEL_MESSAGE_ID_KEY";
      String FILTER = "CHANNEL_FILTER_KEY";
      String CONTENT = "CHANNEL_MESSAGE_CONTENT_KEY";
      String OWNER = "CHANNEL_OWNER_KEY";
    }
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(Secrets.Authentication.CLIENT_ID, ActualValue.Authentication.CLIENT_ID)
        .secret(Secrets.Authentication.TENANT_ID, ActualValue.Authentication.TENANT_ID)
        .secret(Secrets.Authentication.CLIENT_SECRET, ActualValue.Authentication.CLIENT_SECRET)
        .secret(Secrets.Authentication.BEARER_TOKEN, ActualValue.Authentication.BEARER_TOKEN)
        .secret(Secrets.Authentication.REFRESH_TOKEN, ActualValue.Authentication.REFRESH_TOKEN)
        .secret(Secrets.Channel.CHANNEL_ID, ActualValue.Channel.CHANNEL_ID)
        .secret(Secrets.Channel.GROUP_ID, ActualValue.Channel.GROUP_ID)
        .secret(Secrets.Channel.NAME, ActualValue.Channel.NAME)
        .secret(Secrets.Channel.MESSAGE_ID, ActualValue.Channel.MESSAGE_ID)
        .secret(Secrets.Channel.FILTER, ActualValue.Channel.FILTER)
        .secret(Secrets.Channel.OWNER, ActualValue.Channel.OWNER)
        .secret(Secrets.Channel.CONTENT, ActualValue.Channel.CONTENT)
        .secret(Secrets.Chat.CHAT_ID, ActualValue.Chat.CHAT_ID)
        .secret(Secrets.Chat.MESSAGE_ID, ActualValue.Chat.MESSAGE_ID)
        .secret(Secrets.Chat.FILTER, ActualValue.Chat.FILTER)
        .secret(Secrets.Chat.CONTENT_PART_1, ActualValue.Chat.CONTENT_PART_1)
        .secret(Secrets.Chat.CONTENT_PART_2, ActualValue.Chat.CONTENT_PART_2);
  }

  protected static Stream<String> executeSuccessWorkWithChannelTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.SUCCESS_EXECUTE);
  }

  protected static Stream<String> executeSuccessWorkWithChatTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Chat.SUCCESS_EXECUTE);
  }

  protected static Stream<String> parseRequestTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.PARSE_REQUEST);
  }

  protected static Stream<String> createChannelValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.CREATE_VALIDATION_FAIL);
  }

  protected static Stream<String> getChannelValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.GET_VALIDATION_FAIL);
  }

  protected static Stream<String> getChannelMessageValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.GET_MESSAGE_VALIDATION_FAIL);
  }

  protected static Stream<String> listChannelMembersValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.LIST_MEMBERS_VALIDATION_FAIL);
  }

  protected static Stream<String> listChannelMessagesValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.LIST_MESSAGES_VALIDATION_FAIL);
  }

  protected static Stream<String> listChannelsValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.LIST_VALIDATION_FAIL);
  }

  protected static Stream<String> listChannelMessageRepliesValidationFailTestCases()
      throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.LIST_REPLIES_VALIDATION_FAIL);
  }

  protected static Stream<String> sendMessageToChannelValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Channel.SEND_MESSAGE_VALIDATION_FAIL);
  }

  protected static Stream<String> createChatValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Chat.CREATE_VALIDATION_FAIL);
  }

  protected static Stream<String> getChatValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Chat.GET_VALIDATION_FAIL);
  }

  protected static Stream<String> getMessageInChatValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Chat.GET_MESSAGE_VALIDATION_FAIL);
  }

  protected static Stream<String> listChatMembersValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Chat.LIST_MEMBERS_VALIDATION_FAIL);
  }

  protected static Stream<String> listMessagesInChatValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Chat.LIST_MESSAGES_VALIDATION_FAIL);
  }

  protected static Stream<String> sendMessageInChatValidationFailTestCases() throws IOException {
    return loadTestCasesFromResourceFile(TestCasesPath.Chat.SEND_MESSAGE_VALIDATION_FAIL);
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    var array = objectMapper.readValue(cases, ArrayList.class);
    return array.stream()
        .map(
            s -> {
              try {
                return objectMapper.writer().writeValueAsString(s);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .map(Arguments::of);
  }

  protected static void assertValidationException(String input) {
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(MSTeamsRequest.class),
            "Exception was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
