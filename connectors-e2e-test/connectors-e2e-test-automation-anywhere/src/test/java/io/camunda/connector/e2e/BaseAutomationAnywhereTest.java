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
package io.camunda.connector.e2e;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.zeebe.client.ZeebeClient;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

public abstract class BaseAutomationAnywhereTest {

  @TempDir File tempDir;

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired ZeebeClient zeebeClient;

  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  @LocalServerPort int serverPort;

  protected static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/automation-anywhere/element-templates/automation-anywhere-outbound-connector.json";
  protected static final String AUTH_TOKEN_HEADER = "x-authorization";
  protected static final String AUTH_TOKEN = "authToken";
  protected static final String WORK_ITEMS_LIST_URL = "/v3/wlm/queues/12/workitems/list";
  protected static final String AUTHENTICATION_URL = "/v1/authentication";
  protected static final String WORK_ITEMS_ADD_URL = "/v3/wlm/queues/12/workitems";

  protected static final Object AUTHENTICATION_RESPONSE = Map.of("token", "authToken");
  protected static final Object ADD_ITEM_RESPONSE = Map.of("list", List.of(Map.of("id", 31250)));
  protected static final Object GET_ITEM_RESPONSE =
      Map.of("list", List.of(Map.of("status", "READY_TO_RUN")));

  protected static final String EXPECTED_REQUEST_BODY_JSON_WITH_ITEM =
      """
          {
            "workItems": [
              {
                "json": {
                  "last_name": "Doe",
                  "email": "jane.doe@example.com"
                }
              }
            ]
          }
          """;

  protected static final String EXPECTED_REQUEST_BODY_WITH_FILTER =
      """
              {
                "filter" : {
                  "field" : "id",
                  "value" : 31250,
                  "operator" : "eq"
                }
              }
              """;

  protected static final String EXPECTED_PASSWORD_BASED_AUTH_REQUEST =
      """
              {
                "password" : "password",
                "multipleLogin" : true,
                "username" : "username"
              }
              """;
  protected static final String EXPECTED_API_KEY_BASED_AUTH_REQUEST =
      """
              {
                "username" : "apiUserName",
                "apiKey" : "myApiKey"
              }
              """;

  @BeforeEach
  void beforeEach() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }
}
