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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.constants.Constants;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpJsonFunctionProxyTest extends BaseTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";

  private static final String PROXY_FUNCTION_URL = "http://localhost/my-proxy/";

  @Mock private HttpRequestFactory requestFactory;
  @Mock private HttpRequest httpRequest;
  @Mock private HttpResponse httpResponse;

  private HttpJsonFunction functionUnderTest;

  public static String body =
      "{\n"
          + "    \"token\": \"eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiIzMzE3MDciLCJjbGllbnRUeXBlIjoiV0VCIiwibGljZW5zZXMiOlsiRElTQ09WRVJZQk9UQU5BTFlaRVIiLCJBTkFMWVRJQ1NDTElFTlQiLCJERVZFTE9QTUVOVCIsIkRJU0NPVkVSWUJPVFJFQ09SREVSIl0sImFuYWx5dGljc0xpY2Vuc2VzUHVyY2hhc2VkIjp7IkFuYWx5dGljc0NsaWVudCI6dHJ1ZX0sInRlbmFudFV1aWQiOiIwOGI5M2NmZS1hNmRkLTRkNmItOTRhYS05MzY5ZmRkMmEwMjYiLCJoeWJyaWRUZW5hbnQiOiIiLCJtdWx0aXBsZUxvZ2luIjpmYWxzZSwiaWF0IjoxNjc0Njc0MzUzLCJleHAiOjE2NzQ2NzU1NTMsImlzcyI6IkF1dG9tYXRpb25Bbnl3aGVyZSIsIm5hbm9UaW1lIjo0NjU1NzExNjgxMTM3MzU5fQ.RelT70hyGcIW9gHqdIPtfhmBZhzs3ZB64avwGSTHmqfFy4kQ33XDHwzMCla85u6WuU058f4Hc78PvlwNy6SvWbIgjlMi8IK7Ng7_0YK0ch1u2U-IEBjEykhdi7cKnYjjiX9AdZNDHN6omXExAz-kuJGcXWEB_mA0rzURpnb7LlZMrxgGCbLYDKCU7nw9gxEENMBNlnn0Kooxdveg_W6qM2XtpaQFfQ25s6-dM9GNpd1wbyOc5fveY_1vYocJkbOSKA6R_mRLPnndxNfKjzAoEIZvuvFZevFZJdcbxORBxQpppe-3IG4x_tC2N9tHs9sAKfFQ3-vb_BQXs37EGjUupQ\",\n"
          + "    \"user\": {\n"
          + "        \"id\": 331707,\n"
          + "        \"username\": \"mrslilex@gmail.com\",\n"
          + "        \"domain\": null,\n"
          + "        \"firstName\": null,\n"
          + "        \"lastName\": null,\n"
          + "        \"version\": 86,\n"
          + "        \"principalId\": 331707,\n"
          + "        \"deleted\": false,\n"
          + "        \"roles\": [\n"
          + "            {\n"
          + "                \"name\": \"AAE_Bot Insight Expert\",\n"
          + "                \"id\": 1905159,\n"
          + "                \"version\": 1\n"
          + "            },\n"
          + "            {\n"
          + "                \"name\": \"CE_user\",\n"
          + "                \"id\": 1905174,\n"
          + "                \"version\": 0\n"
          + "            }\n"
          + "        ],\n"
          + "        \"sysAssignedRoles\": [],\n"
          + "        \"groupNames\": [],\n"
          + "        \"permissions\": [\n"
          + "            {\n"
          + "                \"id\": 13044559,\n"
          + "                \"action\": \"converttobot\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044527,\n"
          + "                \"action\": \"managecredentials\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"credentials\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044560,\n"
          + "                \"action\": \"createdeletemanualaggregation\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044533,\n"
          + "                \"action\": \"register\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"devices\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044594,\n"
          + "                \"action\": \"import\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"repositorymanager\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044567,\n"
          + "                \"action\": \"editrecording\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044635,\n"
          + "                \"action\": \"viewlearninginstance\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"viewiqbot\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044640,\n"
          + "                \"action\": \"launchvalidatior\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"viewlearninginstance\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044561,\n"
          + "                \"action\": \"createdeleteopportunity\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044531,\n"
          + "                \"action\": \"delete\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"devices\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044563,\n"
          + "                \"action\": \"deleterecording\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044637,\n"
          + "                \"action\": \"createlearninginstances\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"viewlearninginstance\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044574,\n"
          + "                \"action\": \"viewmanualaggregation\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044625,\n"
          + "                \"action\": \"viewuserrolebasicinfo\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"usermanagement\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044528,\n"
          + "                \"action\": \"view\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"dashboard\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044642,\n"
          + "                \"action\": \"trainlearninginstancegroups\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"viewlearninginstance\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044556,\n"
          + "                \"action\": \"view\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"packagemanager\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044624,\n"
          + "                \"action\": \"usermanagement\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"usermanagement\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13046289,\n"
          + "                \"action\": \"runtimeclientsmanagement\",\n"
          + "                \"resourceId\": \"155938\",\n"
          + "                \"resourceType\": \"runtimeclientsmanagement\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044569,\n"
          + "                \"action\": \"updatemanualaggregation\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044523,\n"
          + "                \"action\": \"updateany\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"credentialattributevalue\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044599,\n"
          + "                \"action\": \"view\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"repositorymanager\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044575,\n"
          + "                \"action\": \"viewopportunity\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044568,\n"
          + "                \"action\": \"exportopportunity\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044577,\n"
          + "                \"action\": \"viewrecording\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044578,\n"
          + "                \"action\": \"viewsystemaggregation\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044562,\n"
          + "                \"action\": \"createrecording\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044542,\n"
          + "                \"action\": \"viewiqbot\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"iqbot\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044638,\n"
          + "                \"action\": \"deletelearninginstances\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"viewlearninginstance\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044549,\n"
          + "                \"action\": \"create\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"locker\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044522,\n"
          + "                \"action\": \"botautologinapi\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"credentialattributevalue\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044534,\n"
          + "                \"action\": \"view\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"devices\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044565,\n"
          + "                \"action\": \"editopportunity\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044639,\n"
          + "                \"action\": \"editlearninginstances\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"viewlearninginstance\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044581,\n"
          + "                \"action\": \"create\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"queue\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044641,\n"
          + "                \"action\": \"sendlearninginstancestoprod\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"viewlearninginstance\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044606,\n"
          + "                \"action\": \"calculate\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"sla\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044566,\n"
          + "                \"action\": \"editprocess\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044521,\n"
          + "                \"action\": \"createstandard\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"credentialattribute\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044597,\n"
          + "                \"action\": \"run\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"repositorymanager\"\n"
          + "            },\n"
          + "            {\n"
          + "                \"id\": 13044576,\n"
          + "                \"action\": \"viewprocess\",\n"
          + "                \"resourceId\": null,\n"
          + "                \"resourceType\": \"processdiscovery\"\n"
          + "            }\n"
          + "        ],\n"
          + "        \"licenseFeatures\": [\n"
          + "            \"DISCOVERYBOTANALYZER\",\n"
          + "            \"ANALYTICSCLIENT\",\n"
          + "            \"DEVELOPMENT\",\n"
          + "            \"DISCOVERYBOTRECORDER\"\n"
          + "        ],\n"
          + "        \"emailVerified\": true,\n"
          + "        \"passwordSet\": true,\n"
          + "        \"questionsSet\": false,\n"
          + "        \"enableAutoLogin\": false,\n"
          + "        \"disabled\": false,\n"
          + "        \"clientRegistered\": false,\n"
          + "        \"description\": null,\n"
          + "        \"createdBy\": 331706,\n"
          + "        \"createdOn\": \"2023-01-17T20:34:31Z\",\n"
          + "        \"updatedBy\": 331707,\n"
          + "        \"updatedOn\": \"2023-01-18T12:55:24Z\",\n"
          + "        \"publicKey\": null,\n"
          + "        \"appType\": null,\n"
          + "        \"routingName\": null,\n"
          + "        \"appUrl\": null,\n"
          + "        \"email\": \"mrslilex@gmail.com\",\n"
          + "        \"lastLoginTime\": \"2023-01-20T10:16:47Z\",\n"
          + "        \"deviceCredentialAttested\": false,\n"
          + "        \"multipleLoginAllowed\": false\n"
          + "    },\n"
          + "    \"tenantUuid\": \"08b93cfe-a6dd-4d6b-94aa-9369fdd2a026\",\n"
          + "    \"mfaAuthResponse\": null\n"
          + "}";

  @BeforeEach
  public void setup() {
    functionUnderTest = new HttpJsonFunction(gson, requestFactory, PROXY_FUNCTION_URL);
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldReturnResult_WhenExecuted(final String input) throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    when(requestFactory.buildRequest(
            eq(Constants.POST),
            eq(new GenericUrl(PROXY_FUNCTION_URL)),
            nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    String responseContent = "{ headers: { 'someHeader': 'someValue'}}";
    when(httpResponse.getContent())
        .thenReturn(new ByteArrayInputStream(responseContent.getBytes(StandardCharsets.UTF_8)));
    when(httpRequest.execute()).thenReturn(httpResponse);

    // when
    HttpJsonResult functionCallResponseAsObject =
        (HttpJsonResult) functionUnderTest.execute(context);

    // then
    verify(httpRequest).execute();
    assertThat(functionCallResponseAsObject.getHeaders()).containsEntry("someHeader", "someValue");
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldReuseErrorData_WhenProxyCallFailed(final String input) throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    final var httpException = mock(HttpResponseException.class);
    String errorResponseContent = "{ errorCode: 'XYZ', error: 'some message' }";
    when(httpException.getContent()).thenReturn(errorResponseContent);
    when(httpException.getStatusCode()).thenReturn(500);
    when(httpException.getMessage()).thenReturn("my error message");
    when(requestFactory.buildRequest(
            eq(Constants.POST),
            eq(new GenericUrl(PROXY_FUNCTION_URL)),
            nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    doThrow(httpException).when(httpRequest).execute();
    // when
    final var result = catchThrowable(() -> functionUnderTest.execute(context));
    // then
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessage("some message")
        .extracting("errorCode")
        .isEqualTo("XYZ");
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldUseExceptionData_WhenProxyCallFailed_ErrorDataNoJson(final String input)
      throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    final var httpException = mock(HttpResponseException.class);
    String errorResponseContent = "XYZ";
    when(httpException.getContent()).thenReturn(errorResponseContent);
    when(httpException.getStatusCode()).thenReturn(500);
    when(httpException.getMessage()).thenReturn("my error message");
    when(requestFactory.buildRequest(
            eq(Constants.POST),
            eq(new GenericUrl(PROXY_FUNCTION_URL)),
            nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    doThrow(httpException).when(httpRequest).execute();
    // when
    final var result = catchThrowable(() -> functionUnderTest.execute(context));
    // then
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessage("my error message")
        .extracting("errorCode")
        .isEqualTo("500");
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  void shouldUseExceptionData_WhenProxyCallFailed_NoErrorData(final String input)
      throws IOException {
    // given - minimal required entity
    final var context =
        OutboundConnectorContextBuilder.create().variables(input).secrets(name -> "foo").build();

    final var httpException = mock(HttpResponseException.class);
    when(httpException.getStatusCode()).thenReturn(500);
    when(httpException.getMessage()).thenReturn("my error message");
    when(requestFactory.buildRequest(
            eq(Constants.POST),
            eq(new GenericUrl(PROXY_FUNCTION_URL)),
            nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    doThrow(httpException).when(httpRequest).execute();
    // when
    final var result = catchThrowable(() -> functionUnderTest.execute(context));
    // then
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessage("my error message")
        .extracting("errorCode")
        .isEqualTo("500");
  }

  private static Stream<String> successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  //  @Test
  //  public void test() {
  //
  //    JsonElement jsonResult = gson.fromJson(body, JsonElement.class);
  //    String fromRequest = "asObject:user.asObject:permissions.asArray:1.asObject:id.asString";
  //    String[] fromRequests = fromRequest.split("\\.");
  //
  //    String result = null;
  //    for (final String request : fromRequests) {
  //      String[] split = request.split(":");
  //      if (split[0].equalsIgnoreCase("asObject")) {
  //        jsonResult = jsonResult.getAsJsonObject().get(split[1]);
  //      }
  //      if (split[0].equalsIgnoreCase("asArray")) {
  //        jsonResult = jsonResult.getAsJsonArray().get(Integer.parseInt(split[1]));
  //      }
  //      if (split[0].equalsIgnoreCase("asString")) {
  //        result = jsonResult.getAsString();
  //      }
  //    }
  //
  //    System.out.println(result);
  //  }
}
