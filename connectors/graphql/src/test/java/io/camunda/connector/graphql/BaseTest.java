/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.graphql.components.GsonComponentSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class BaseTest {

  protected Gson gson = GsonComponentSupplier.gsonInstance();

  protected GsonFactory gsonFactory = GsonComponentSupplier.gsonFactoryInstance();

  protected interface SecretsConstant {
    String URL = "URL_KEY";
    String METHOD = "METHOD_KEY";
    String CONNECT_TIMEOUT = "CONNECT_TIMEOUT_KEY";

    interface Authentication {
      String TOKEN = "TOKEN_KEY";
      String PASSWORD = "PASSWORD_KEY";
      String USERNAME = "USERNAME_KEY";
      String OAUTH_TOKEN_ENDPOINT = "OAUTH_TOKEN_ENDPOINT_KEY";
      String CLIENT_ID = "CLIENT_ID_KEY";
      String CLIENT_SECRET = "CLIENT_SECRET_KEY";
      String AUDIENCE = "AUDIENCE_KEY";
    }

    interface Variables {
      String ID = "VARIABLE_ID";
    }

    interface Query {
      String ID = "QUERY_ID";
      String TEXT = "TEXT_KEY";
      String TEXT_PART_1 = "TEXT_PART_1_KEY";
      String TEXT_PART_2 = "TEXT_PART_2_KEY";
      String TEXT_PART_3 = "TEXT_PART_3_KEY";
    }
  }

  protected interface ActualValue {
    String URL = "https://camunda.io/http-endpoint";
    String METHOD = "GET";
    String CONNECT_TIMEOUT = "50";

    interface Authentication {
      String TOKEN = "test token";
      String PASSWORD = "1234567890";
      String USERNAME = "test username";
      String OAUTH_TOKEN_ENDPOINT = "https://test/api/v2/";
      String CLIENT_ID = "bi1cekB123456GRWBBEgzdxA89S2T";
      String CLIENT_SECRET = "Bzw6SL12345678934562eqg4fJM72EeeM2JQiF4BfbyYZUDCur7ntB";
      String AUDIENCE = "https://test/api/v2/";
    }

    interface Variables {
      String ID = "variableId";
      String USER_AGENT = "http-connector-demo";
    }

    interface Query {
      String ID = "secret id key";
      String CUSTOMER_NAME_SECRET = "secret name";
      String CUSTOMER_NAME_REAL = CUSTOMER_NAME_SECRET + " plus some text";
      String CUSTOMER_EMAIL_SECRET = "start email plus secret email part plus end email";
      String CUSTOMER_EMAIL_REAL = "start email plus " + CUSTOMER_EMAIL_SECRET + " plus end email";
      String TEXT_PART_1 = "start secret text plus ";
      String TEXT_PART_2 = "mid of text plus ";
      String TEXT_PART_3 = "end of text";
      String TEXT = TEXT_PART_1 + TEXT_PART_2 + TEXT_PART_3;
    }
  }

  protected interface JsonKeys {
    String CLUSTER_ID = "X-Camunda-Cluster-ID";
    String USER_AGENT = "User-Agent";
    String QUERY = "q";
    String PRIORITY = "priority";
    String CUSTOMER = "customer";
    String ID = "id";
    String NAME = "name";
    String EMAIL = "email";
    String TEXT = "text";
  }

  protected OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.URL, ActualValue.URL)
        .secret(SecretsConstant.METHOD, ActualValue.METHOD)
        .secret(SecretsConstant.CONNECT_TIMEOUT, ActualValue.CONNECT_TIMEOUT)
        .secret(SecretsConstant.Authentication.TOKEN, ActualValue.Authentication.TOKEN)
        .secret(SecretsConstant.Authentication.USERNAME, ActualValue.Authentication.USERNAME)
        .secret(SecretsConstant.Authentication.PASSWORD, ActualValue.Authentication.PASSWORD)
        .secret(
            SecretsConstant.Authentication.OAUTH_TOKEN_ENDPOINT,
            ActualValue.Authentication.OAUTH_TOKEN_ENDPOINT)
        .secret(SecretsConstant.Authentication.CLIENT_ID, ActualValue.Authentication.CLIENT_ID)
        .secret(
            SecretsConstant.Authentication.CLIENT_SECRET, ActualValue.Authentication.CLIENT_SECRET)
        .secret(SecretsConstant.Authentication.AUDIENCE, ActualValue.Authentication.AUDIENCE)
        .secret(SecretsConstant.Variables.ID, ActualValue.Variables.ID)
        .secret(SecretsConstant.Query.ID, ActualValue.Query.ID)
        .secret(SecretsConstant.Query.TEXT, ActualValue.Query.TEXT)
        .secret(SecretsConstant.Query.TEXT_PART_1, ActualValue.Query.TEXT_PART_1)
        .secret(SecretsConstant.Query.TEXT_PART_2, ActualValue.Query.TEXT_PART_2)
        .secret(SecretsConstant.Query.TEXT_PART_3, ActualValue.Query.TEXT_PART_3);
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final Gson testingGson = GsonComponentSupplier.gsonInstance();
    var array = testingGson.fromJson(cases, ArrayList.class);
    return array.stream().map(testingGson::toJson).map(Arguments::of);
  }
}
