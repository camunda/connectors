/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.gson.Gson;
import io.camunda.connector.suppliers.GsonSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  protected static final Gson gson = GsonSupplier.getGson();

  interface ActualValue {

    interface Authentication {
      String CLIENT_ID = "abc01234-0c7f-012c-9876-testClientId";
      String TENANT_ID = "5357c0a8-4728-418b-8c41-testTenantId";
      String CLIENT_SECRET = "Wao8Q~yzXYfgdM_testClientSecret_RZyqPaET";
    }

    interface ChatData {
      String CHAT_ID = "19:1c5b01696d2e4a179c292bc9cf04e63b@thread.v2";
      String CONTENT_PART_1 = "microsoft teams";
      String CONTENT_PART_2 = "camunda connector";
      String CONTENT = "Hi " + CONTENT_PART_1 + " from " + CONTENT_PART_2 + "!!!";
    }

    interface ChannelData {
      String CHANNEL_ID = "abc01234-0c7f-012c-9876-testClientId";
      String GROUP_ID = "19:1c5b01696d2e4a179c292bc9cf04e63b@thread.v2";
      String NAME = "ChannelTest";
      String DESCRIPTION = "Test channel description";
      String CHANNEL_TYPE = "private";
    }
  }

  interface Secrets {

    interface Authentication {
      String CLIENT_ID = "CLIENT_ID_KEY";
      String TENANT_ID = "TENANT_ID_KEY";
      String CLIENT_SECRET = "CLIENT_SECRET_ID_KEY";
    }

    interface ChatData {
      String CHAT_ID = "CHAT_ID_KEY";
      String CONTENT_PART_1 = "MICROSOFT_TEAMS_KEY";
      String CONTENT_PART_2 = "CAMUNDA_CONNECTOR_KEY";
    }

    interface ChannelData {
      String CHANNEL_ID = "CHANNEL_ID_KEY";
      String TEAM_ID = "TEAM_ID";
      String NAME = "NAME";
      String DESCRIPTION = "DESCRIPTION";
      String CHANNEL_TYPE = "CHANNEL_TYPE";
    }
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(Secrets.Authentication.CLIENT_ID, ActualValue.Authentication.CLIENT_ID)
        .secret(Secrets.Authentication.TENANT_ID, ActualValue.Authentication.TENANT_ID)
        .secret(Secrets.Authentication.CLIENT_SECRET, ActualValue.Authentication.CLIENT_SECRET)
        .secret(Secrets.ChatData.CHAT_ID, ActualValue.ChatData.CHAT_ID)
        .secret(Secrets.ChannelData.CHANNEL_ID, ActualValue.ChannelData.CHANNEL_ID)
        .secret(Secrets.ChannelData.TEAM_ID, ActualValue.ChannelData.GROUP_ID)
        .secret(Secrets.ChatData.CONTENT_PART_1, ActualValue.ChatData.CONTENT_PART_1)
        .secret(Secrets.ChatData.CONTENT_PART_2, ActualValue.ChatData.CONTENT_PART_2);
  }

  protected static Stream<String> executeSendMessageToChat() throws IOException {
    return loadTestCasesFromResourceFile("");
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
