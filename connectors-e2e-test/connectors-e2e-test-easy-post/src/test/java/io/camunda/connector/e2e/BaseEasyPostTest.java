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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

public abstract class BaseEasyPostTest {
  protected static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/easy-post/element-templates/easy-post-connector.json";

  protected static final String API_KEY = "easy-post-api-key";

  protected static final String API_KEY_ENCODED =
      Base64.getEncoder().encodeToString((API_KEY + ":").getBytes());

  // JSON request bodies
  protected static final String ADDRESS_REQUEST_BODY_JSON = createAddressRequestBodyJson();
  protected static final String PARCEL_REQUEST_BODY_JSON = createParcelRequestBodyJson();
  protected static final String SHIPMENT_REQUEST_BODY_JSON = createShipmentRequestBodyJson();
  protected static final String BUY_SHIPMENT_REQUEST_BODY_JSON =
      "{\"rate\": {\"id\":\"rate_123456789\"}}";
  // Mock responses
  protected static final Map<String, Object> CREATE_ADDRESS_RESPONSE = createAddressResponse();
  protected static final Map<String, Object> CREATE_PARCEL_RESPONSE = createParcelResponse();
  protected static final Map<String, Object> CREATE_SHIPMENT_RESPONSE = createShipmentResponse();
  protected static final Map<String, Object> BUY_SHIPMENT_RESPONSE = createBuyShipmentResponse();
  protected static final Map<String, Object> VERIFY_ADDRESS_RESPONSE =
      createVerifyAddressResponse();
  protected static final Map<String, Object> RETRIEVE_TRACKER_RESPONSE =
      createRetrieveTrackerResponse();

  @TempDir File tempDir;

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired ZeebeClient zeebeClient;

  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  @LocalServerPort int serverPort;

  @BeforeEach
  void beforeEach() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }

  protected ZeebeTest setupTestWithBpmnModel(String taskName, File elementTemplate) {
    BpmnModelInstance model = getBpmnModelInstance(taskName);
    BpmnModelInstance updatedModel = getBpmnModelInstance(model, elementTemplate, taskName);
    return getZeebeTest(updatedModel);
  }

  protected static BpmnModelInstance getBpmnModelInstance(final String serviceTaskName) {
    return Bpmn.createProcess()
        .executable()
        .startEvent()
        .serviceTask(serviceTaskName)
        .endEvent()
        .done();
  }

  protected ZeebeTest getZeebeTest(final BpmnModelInstance updatedModel) {
    return ZeebeTest.with(zeebeClient)
        .deploy(updatedModel)
        .createInstance()
        .waitForProcessCompletion();
  }

  protected BpmnModelInstance getBpmnModelInstance(
      final BpmnModelInstance model, final File elementTemplate, final String taskName) {
    return new BpmnFile(model)
        .writeToFile(new File(tempDir, "test.bpmn"))
        .apply(elementTemplate, taskName, new File(tempDir, "result.bpmn"));
  }

  private static String createAddressRequestBodyJson() {
    return """
                    {
                      "address": {
                        "city": "San Francisco",
                        "name": "John Doe",
                        "country": "US",
                        "street2": "5th Floor",
                        "company": "Doe Inc.",
                        "phone": "415-123-4567",
                        "state_tax_id": "987-654321",
                        "zip": "94104",
                        "email": "johndoe@example.com",
                        "state": "CA",
                        "street1": "417 Montgomery Street",
                        "federal_tax_id": "12-3456789"
                      }
                    }
                              """;
  }

  private static String createParcelRequestBodyJson() {
    return """
                    {
                      "parcel": {
                        "weight" : "5",
                        "predefined_package" : "predefinedPackage",
                        "height" : "15",
                        "length" : "10",
                        "width" : "20"
                      }
                    }
                    """;
  }

  private static String createShipmentRequestBodyJson() {
    return """
            {
              "shipment": {
              "to_address" : {
              "id": "adr_64cfa74a8a5a1"
              },
              "from_address" : {
              "id": "adr_64cfa7fde45"
              },
              "parcel" : {
              "id": "prcl_4d0bf800c0e6cb"
              }
              }
            }

            """;
  }

  private static Map<String, Object> createAddressResponse() {
    return Map.of("id", "expectedAddressId", "object", "Address");
  }

  private static Map<String, Object> createParcelResponse() {
    return Map.of("id", "expectedParcelId", "object", "Parcel");
  }

  private static Map<String, Object> createShipmentResponse() {
    return Map.of(
        "id",
        "shp_123456789",
        "object",
        "Shipment",
        "created_at",
        "2021-01-01T12:00:00Z",
        "to_address",
        Map.of("id", "adr_64cfa74a8a5a1"),
        "from_address",
        Map.of("id", "adr_64cfa7fde45"),
        "parcel",
        Map.of(
            "id", "prcl_4d0bf800c0e6cb",
            "object", "Parcel"));
  }

  private static Map<String, Object> createBuyShipmentResponse() {
    return Map.of(
        "id", "shp_123456789",
        "object", "Shipment",
        "status", "purchased",
        "tracking_code", "EZ1000000002",
        "tracker", Map.of("id", "trk_c1aa7f4bfa71414887e5e87e0138fb98", "object", "Tracker"));
  }

  private static Map<String, Object> createVerifyAddressResponse() {
    return Map.of(
        "address",
        Map.of(
            "id", "adr_64cfa7fde45", "verifications", Map.of("delivery", Map.of("success", true))));
  }

  private static Map<String, Object> createRetrieveTrackerResponse() {
    return Map.of(
        "id", "trk_21fcba38905f4fc4bff0585ce55860ab",
        "object", "Tracker",
        "tracking_code", "9405500105440282587999",
        "status", "pre_transit");
  }
}
