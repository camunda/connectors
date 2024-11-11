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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@ExtendWith(MockitoExtension.class)
public class EasyPostTests extends BaseEasyPostTest {

  @Test
  void createAddressTest() {
    wm.stubFor(
        post(urlPathMatching("/v2/addresses"))
            .withHeader("Authorization", matching("Basic " + API_KEY_ENCODED))
            .withRequestBody(WireMock.equalToJson(ADDRESS_REQUEST_BODY_JSON, true, false))
            .willReturn(ResponseDefinitionBuilder.okForJson(CREATE_ADDRESS_RESPONSE)));

    var elementTemplate =
        getElementTemplateWithBasicAuthForOperationType("createAddress")
            .property("body.address.name", "John Doe")
            .property("body.address.company", "Doe Inc.")
            .property("body.address.phone", "415-123-4567")
            .property("body.address.street1", "417 Montgomery Street")
            .property("body.address.street2", "5th Floor")
            .property("body.address.city", "San Francisco")
            .property("body.address.state", "CA")
            .property("body.address.zip", "94104")
            .property("body.address.country", "US")
            .property("body.address.email", "johndoe@example.com")
            .property("body.address.federal_tax_id", "12-3456789")
            .property("body.address.state_tax_id", "987-654321")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("easyPostCreateAddressTask", elementTemplate);

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("addressId", "expectedAddressId");
  }

  @Test
  void createParcelTest() {
    wm.stubFor(
        post(urlPathMatching("/v2/parcels"))
            .withHeader("Authorization", matching("Basic " + API_KEY_ENCODED))
            .withRequestBody(WireMock.equalToJson(PARCEL_REQUEST_BODY_JSON, true, false))
            .willReturn(ResponseDefinitionBuilder.okForJson(CREATE_PARCEL_RESPONSE)));

    var elementTemplate =
        getElementTemplateWithBasicAuthForOperationType("createParcel")
            .property("body.parcel.length", "10")
            .property("body.parcel.width", "20")
            .property("body.parcel.height", "15")
            .property("body.parcel.weight", "5")
            .property("body.parcel.predefined_package", "predefinedPackage")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("easyPostCreateParcelTask", elementTemplate);

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("parcelId", "expectedParcelId");
  }

  @Test
  void createShipmentTest() {
    wm.stubFor(
        post(urlPathMatching("/v2/shipments"))
            .withHeader("Authorization", matching("Basic " + API_KEY_ENCODED))
            .withRequestBody(WireMock.equalToJson(SHIPMENT_REQUEST_BODY_JSON, true, false))
            .willReturn(
                ResponseDefinitionBuilder.okForJson(CREATE_SHIPMENT_RESPONSE).withStatus(201)));

    var elementTemplate =
        getElementTemplateWithBasicAuthForOperationType("createShipment")
            .property("body.shipment.to_address.id", "adr_64cfa74a8a5a1")
            .property("body.shipment.from_address.id", "adr_64cfa7fde45")
            .property("body.shipment.parcel.id", "prcl_4d0bf800c0e6cb")
            .property("resultExpression", "={shipmentId:response.body.id}")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("easyPostCreateShipmentTask", elementTemplate);

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("shipmentId", "shp_123456789");
  }

  @Test
  void buyShipmentTest() {
    wm.stubFor(
        post(urlPathMatching("/v2/shipments/shp_123456789/buy"))
            .withHeader("Authorization", matching("Basic " + API_KEY_ENCODED))
            .withRequestBody(WireMock.equalToJson(BUY_SHIPMENT_REQUEST_BODY_JSON, true, false))
            .willReturn(ResponseDefinitionBuilder.okForJson(BUY_SHIPMENT_RESPONSE)));

    var elementTemplate =
        getElementTemplateWithBasicAuthForOperationType("buyShipment")
            .property("shipmentIdValue", "shp_123456789")
            .property("body.rate.id", "rate_123456789")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("easyPostBuyShipmentTask", elementTemplate);

    assertThat(bpmnTest.getProcessInstanceEvent())
        .hasVariable("trackerId", "trk_c1aa7f4bfa71414887e5e87e0138fb98");
  }

  @Test
  void verifyCreatedAddressTest() {
    String addressId = "adr_64cfa7fde45"; // The addressId to verify
    String path = "/v2/addresses/" + addressId + "/verify";

    wm.stubFor(
        get(urlPathMatching(path))
            .withHeader("Authorization", matching("Basic " + API_KEY_ENCODED))
            .willReturn(ResponseDefinitionBuilder.okForJson(VERIFY_ADDRESS_RESPONSE)));

    var elementTemplate =
        getElementTemplateWithBasicAuthForOperationType("verifyAddressById")
            .property("addressIdValue", addressId)
            .property(
                "resultExpression",
                "={addressDeliverySuccess: response.body.address.verifications.delivery.success}")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("easyPostVerifyAddressTask", elementTemplate);

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("addressDeliverySuccess", true);
  }

  @Test
  void retrieveTrackerByIdTest() {
    wm.stubFor(
        get(urlPathMatching("/v2/trackers/trk_21fcba38905f4fc4bff0585ce55860ab"))
            .withHeader("Authorization", matching("Basic " + API_KEY_ENCODED))
            .willReturn(ResponseDefinitionBuilder.okForJson(RETRIEVE_TRACKER_RESPONSE)));

    var elementTemplate =
        getElementTemplateWithBasicAuthForOperationType("retrieveTracker")
            .property("trackerIdValue", "trk_21fcba38905f4fc4bff0585ce55860ab")
            .writeTo(new File(tempDir, "template.json"));

    ZeebeTest bpmnTest = setupTestWithBpmnModel("easyPostRetrieveTrackerTask", elementTemplate);

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("trackerStatus", "pre_transit");
  }

  private ElementTemplate getElementTemplateWithBasicAuthForOperationType(String operationType) {
    return ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
        .property("operationType", operationType)
        .property("authentication.type", "basic")
        .property("authentication.username", API_KEY)
        .property("baseUrl", "http://localhost:" + wm.getPort());
  }
}
