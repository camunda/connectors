/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.NoAuthentication;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutomationAnywhereConnectorTest extends BaseTest {

  private OutboundConnectorFunction connector;
  private HttpCommonResult successHttpCommonResult;
  @Mock private HttpCommonResult httpCommonResult;
  @Mock private HttpService httpService;
  @Captor private ArgumentCaptor<HttpCommonRequest> requestCaptor;

  @BeforeAll
  public static void beforeAll() throws IOException {
    objectMapper = ConnectorsObjectMapperSupplier.getCopy();

    authenticationResponse =
        objectMapper.readValue(
            loadTestCasesFromResourceFile(SUCCESS_AUTHENTICATION_RESPONSE_PATH)
                .findAny()
                .orElseThrow(),
            Object.class);
  }

  @BeforeEach
  public void beforeEach() {
    connector = new AutomationAnywhereConnector(httpService, objectMapper);
    successHttpCommonResult = new HttpCommonResult(200, null, null);
  }

  @ParameterizedTest
  @MethodSource("successPasswordBasedWithSecretsCases")
  public void execute_passwordBasedAuthenticationShouldReturnSuccessResult(String input)
      throws Exception {
    // given
    OutboundConnectorContext context = getContextWithSecrets().variables(input).build();
    when(httpService.executeConnectorRequest(requestCaptor.capture()))
        .thenReturn(httpCommonResult)
        .thenReturn(successHttpCommonResult);

    when(httpCommonResult.body()).thenReturn(authenticationResponse);
    // when
    Object execute = connector.execute(context);
    // then
    verify(httpService, times(2)).executeConnectorRequest(any(HttpCommonRequest.class));

    assertThat(execute).isNotNull().isInstanceOf(HttpCommonResult.class);
    assertThat(((HttpCommonResult) execute).status()).isEqualTo(STATUS_SUCCESS);

    List<HttpCommonRequest> allValues = requestCaptor.getAllValues();
    HttpCommonRequest authRequest = allValues.get(0);
    verifyRequest(authRequest, AUTH_URL, EXPECTED_PASSWORD_BODY);

    HttpCommonRequest operationRequest = allValues.get(1);
    verifyRequest(operationRequest, EXPECTED_CREATE_ITEM_URL, EXPECTED_BODY_WITH_ITEM);
    verifyThatHeaderContainsToken(operationRequest);
  }

  @ParameterizedTest
  @MethodSource("successApiKeyAuthWithSecretsCases")
  public void execute_apiKeyAuthenticationShouldReturnSuccessResult(String input) throws Exception {
    // given
    OutboundConnectorContext context = getContextWithSecrets().variables(input).build();
    when(httpService.executeConnectorRequest(requestCaptor.capture()))
        .thenReturn(httpCommonResult)
        .thenReturn(successHttpCommonResult);

    when(httpCommonResult.body()).thenReturn(authenticationResponse);

    // when
    Object execute = connector.execute(context);

    // then
    verify(httpService, times(2)).executeConnectorRequest(any(HttpCommonRequest.class));

    assertThat(execute).isNotNull().isInstanceOf(HttpCommonResult.class);
    assertThat(((HttpCommonResult) execute).status()).isEqualTo(STATUS_SUCCESS);

    List<HttpCommonRequest> allValues = requestCaptor.getAllValues();

    HttpCommonRequest authRequest = allValues.get(0);
    verifyRequest(authRequest, AUTH_URL, EXPECTED_API_KEY_BODY);

    HttpCommonRequest operationRequest = allValues.get(1);
    verifyRequest(operationRequest, EXPECTED_GET_ITEM_URL, EXPECTED_BODY_WITH_FILTER);
    verifyThatHeaderContainsToken(operationRequest);
  }

  @ParameterizedTest
  @MethodSource("successTokenBasedAuthWithSecretsCases")
  public void execute_tokenAuthenticationShouldReturnSuccessResult(String input) throws Exception {
    // given
    OutboundConnectorContext context = getContextWithSecrets().variables(input).build();
    when(httpService.executeConnectorRequest(requestCaptor.capture()))
        .thenReturn(successHttpCommonResult);
    // when
    Object execute = connector.execute(context);
    // then
    verify(httpService, times(1)).executeConnectorRequest(any(HttpCommonRequest.class));

    assertThat(execute).isNotNull().isInstanceOf(HttpCommonResult.class);
    assertThat(((HttpCommonResult) execute).status()).isEqualTo(STATUS_SUCCESS);

    HttpCommonRequest operationRequest = requestCaptor.getValue();
    verifyRequest(operationRequest, EXPECTED_GET_ITEM_URL, EXPECTED_BODY_WITH_FILTER);
    verifyThatHeaderContainsToken(operationRequest);
  }

  private void verifyRequest(HttpCommonRequest request, String url, Object expectedBody) {
    assertThat(request.getAuthentication()).isInstanceOf(NoAuthentication.class);
    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
    assertThat(request.getUrl()).isEqualTo(url);
    assertThat(request.getConnectionTimeoutInSeconds()).isEqualTo(TIMEOUT);
    assertThat(request.getBody()).isEqualTo(expectedBody);
  }

  private void verifyThatHeaderContainsToken(HttpCommonRequest request) {
    assertThat(request.getHeaders().get(AutomationAnywhereConnector.AUTHORIZATION_KEY))
        .isEqualTo("thisIsTestToken.0123344567890qwertyuiopASDFGHJKLxcvbnm-Ug");
  }
}
