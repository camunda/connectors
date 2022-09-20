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

    interface Authentication {
      String TOKEN = "TOKEN_KEY";
      String PASSWORD = "PASSWORD_KEY";
      String USERNAME = "USERNAME_KEY";
    }

    interface QueryParameters {
      String QUEUE = "QUERY_PARAMETER_KEY";
      String PRIORITY = "PRIORITY_KEY";
    }

    interface Headers {
      String ID = "CLUSTER_ID_KEY";
      String USER_AGENT = "USER_AGENT_KEY";
    }
  }

  protected interface ActualValue {
    String URL = "https://camunda.io/http-endpoint";
    String METHOD = "GET";

    interface Authentication {
      String TOKEN = "test token";
      String PASSWORD = "1234567890";
      String USERNAME = "test username";
    }

    interface QueryParameters {
      String QUEUE = "testQueue";
      String PRIORITY = "12";
    }

    interface Headers {
      String CLUSTER_ID = "testClusterId";
      String USER_AGENT = "http-connector-demo";
    }
  }

  protected interface JsonKeys {
    String CLUSTER_ID = "X-Camunda-Cluster-ID";
    String USER_AGENT = "User-Agent";
    String QUERY = "q";
    String PRIORITY = "priority";
  }

  protected OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.URL, ActualValue.URL)
        .secret(SecretsConstant.METHOD, ActualValue.METHOD)
        .secret(SecretsConstant.Authentication.TOKEN, ActualValue.Authentication.TOKEN)
        .secret(SecretsConstant.Authentication.USERNAME, ActualValue.Authentication.USERNAME)
        .secret(SecretsConstant.Authentication.PASSWORD, ActualValue.Authentication.PASSWORD)
        .secret(SecretsConstant.QueryParameters.QUEUE, ActualValue.QueryParameters.QUEUE)
        .secret(SecretsConstant.QueryParameters.PRIORITY, ActualValue.QueryParameters.PRIORITY)
        .secret(SecretsConstant.Headers.ID, ActualValue.Headers.CLUSTER_ID)
        .secret(SecretsConstant.Headers.USER_AGENT, ActualValue.Headers.USER_AGENT);
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
