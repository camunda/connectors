/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.gson.Gson;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class BaseTest {

  protected Gson gson = GsonComponentSupplier.gsonInstance();

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
      String CLIENT_AUTHENTICATION = "CLIENT_AUTHENTICATION_KEY";
      String AUDIENCE = "AUDIENCE_KEY";
    }

    interface QueryParameters {
      String QUEUE = "QUERY_PARAMETER_KEY";
      String PRIORITY = "PRIORITY_KEY";
    }

    interface Headers {
      String ID = "CLUSTER_ID_KEY";
      String USER_AGENT = "USER_AGENT_KEY";
    }

    interface Body {
      String CUSTOMER_ID = "CUSTOMER_ID_KEY";
      String CUSTOMER_NAME = "NAME_KEY";
      String CUSTOMER_EMAIL = "EMAIL_KEY";
      String TEXT = "TEXT_KEY";
      String TEXT_PART_1 = "TEXT_PART_1_KEY";
      String TEXT_PART_2 = "TEXT_PART_2_KEY";
      String TEXT_PART_3 = "TEXT_PART_3_KEY";
    }
  }

  protected interface ActualValue {
    String URL = "https://camunda.io/http-endpoint";
    String URL_WITH_PATH = "https://camunda.io/http-endpoint/path";
    String METHOD = "GET";
    String CONNECT_TIMEOUT = "50";

    interface Authentication {
      String TOKEN = "test token";
      String PASSWORD = "1234567890";
      String USERNAME = "test username";
      String OAUTH_TOKEN_ENDPOINT = "https://test/api/v2/";
      String CLIENT_ID = "bi1cekB123456GRWBBEgzdxA89S2T";
      String CLIENT_SECRET = "Bzw6SL12345678934562eqg4fJM72EeeM2JQiF4BfbyYZUDCur7ntB";
      String CLIENT_AUTHENTICATION = "Bearer test token";
      String AUDIENCE = "https://test/api/v2/";
    }

    interface QueryParameters {
      String QUEUE = "testQueue";
      String PRIORITY = "12";
    }

    interface Headers {
      String CLUSTER_ID = "testClusterId";
      String USER_AGENT = "http-connector-demo";
    }

    interface Body {
      String CUSTOMER_ID_SECRET = "secret id key";
      String CUSTOMER_ID_REAL = "startId plus " + CUSTOMER_ID_SECRET;
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
        .secret(
            SecretsConstant.Authentication.CLIENT_AUTHENTICATION,
            ActualValue.Authentication.CLIENT_AUTHENTICATION)
        .secret(SecretsConstant.Authentication.AUDIENCE, ActualValue.Authentication.AUDIENCE)
        .secret(SecretsConstant.QueryParameters.QUEUE, ActualValue.QueryParameters.QUEUE)
        .secret(SecretsConstant.QueryParameters.PRIORITY, ActualValue.QueryParameters.PRIORITY)
        .secret(SecretsConstant.Headers.ID, ActualValue.Headers.CLUSTER_ID)
        .secret(SecretsConstant.Headers.USER_AGENT, ActualValue.Headers.USER_AGENT)
        .secret(SecretsConstant.Body.CUSTOMER_NAME, ActualValue.Body.CUSTOMER_NAME_SECRET)
        .secret(SecretsConstant.Body.CUSTOMER_EMAIL, ActualValue.Body.CUSTOMER_EMAIL_SECRET)
        .secret(SecretsConstant.Body.CUSTOMER_ID, ActualValue.Body.CUSTOMER_ID_SECRET)
        .secret(SecretsConstant.Body.TEXT, ActualValue.Body.TEXT)
        .secret(SecretsConstant.Body.TEXT_PART_1, ActualValue.Body.TEXT_PART_1)
        .secret(SecretsConstant.Body.TEXT_PART_2, ActualValue.Body.TEXT_PART_2)
        .secret(SecretsConstant.Body.TEXT_PART_3, ActualValue.Body.TEXT_PART_3);
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
