/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static io.camunda.connector.BaseTest.ACTUAL_ACCESS_KEY;
import static io.camunda.connector.BaseTest.ACTUAL_SECRET_KEY;
import static io.camunda.connector.BaseTest.ACTUAL_TOPIC_ARN;
import static io.camunda.connector.BaseTest.ACTUAL_TOPIC_REGION;
import static io.camunda.connector.BaseTest.AWS_ACCESS_KEY;
import static io.camunda.connector.BaseTest.AWS_SECRET_KEY;
import static io.camunda.connector.BaseTest.AWS_TOPIC_ARN;
import static io.camunda.connector.BaseTest.GSON;
import static io.camunda.connector.BaseTest.MSG_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.SnsConnectorRequest;
import io.camunda.connector.model.SnsConnectorResult;
import io.camunda.connector.suppliers.GsonComponentSupplier;
import io.camunda.connector.suppliers.SnsClientSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnsConnectorFunctionParametrizedTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";

  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";

  @Mock private SnsClientSupplier snsClientSupplier;
  @Mock private AmazonSNS snsClient;
  @Captor private ArgumentCaptor<PublishRequest> publishRequest;

  private SnsConnectorFunction function;

  private static Stream<String> successRequestCases() throws IOException {
    return loadRequestCasesFromFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> failRequestCases() throws IOException {
    return loadRequestCasesFromFile(FAIL_CASES_RESOURCE_PATH);
  }

  @SuppressWarnings("unchecked")
  private static Stream<String> loadRequestCasesFromFile(final String fileName) throws IOException {
    final String cases = readString(new File(fileName).toPath(), UTF_8);
    var array = GSON.fromJson(cases, ArrayList.class);
    return array.stream().map(GSON::toJson).map(Arguments::of);
  }

  @BeforeEach
  void setup() {
    function = new SnsConnectorFunction(snsClientSupplier, GsonComponentSupplier.gsonInstance());
  }

  @ParameterizedTest
  @MethodSource("successRequestCases")
  void execute_ShouldSucceedSuccessCases(final String incomingJson) {
    // given
    SnsConnectorRequest expectedRequest = GSON.fromJson(incomingJson, SnsConnectorRequest.class);
    when(snsClientSupplier.snsClient(ACTUAL_ACCESS_KEY, ACTUAL_SECRET_KEY, ACTUAL_TOPIC_REGION))
        .thenReturn(snsClient);
    PublishResult publishResult = mock(PublishResult.class);
    when(publishResult.getMessageId()).thenReturn(MSG_ID);
    when(snsClient.publish(publishRequest.capture())).thenReturn(publishResult);
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(incomingJson)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_TOPIC_ARN, ACTUAL_TOPIC_ARN)
            .build();
    // when
    Object connectorResultObject = function.execute(ctx);
    PublishRequest initialRequest = publishRequest.getValue();

    // then
    assertThat(connectorResultObject).isInstanceOf(SnsConnectorResult.class);
    SnsConnectorResult connectorResult = (SnsConnectorResult) connectorResultObject;
    assertThat(connectorResult.getMessageId()).isEqualTo(MSG_ID);
    assertThat(initialRequest.getMessage())
        .isEqualTo(expectedRequest.getTopic().getMessage().toString());
  }

  @ParameterizedTest
  @MethodSource("failRequestCases")
  void execute_ShouldThrowExceptionOnMalformedRequests(final String incomingJson) {
    // given
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(incomingJson)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_TOPIC_ARN, ACTUAL_TOPIC_ARN)
            .build();

    // when & then
    assertThrows(Exception.class, () -> function.execute(ctx));
  }
}
