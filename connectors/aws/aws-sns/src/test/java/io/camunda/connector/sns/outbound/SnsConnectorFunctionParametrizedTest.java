/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound;

import static io.camunda.connector.sns.outbound.BaseTest.ACTUAL_ACCESS_KEY;
import static io.camunda.connector.sns.outbound.BaseTest.ACTUAL_SECRET_KEY;
import static io.camunda.connector.sns.outbound.BaseTest.ACTUAL_TOPIC_ARN;
import static io.camunda.connector.sns.outbound.BaseTest.ACTUAL_TOPIC_REGION;
import static io.camunda.connector.sns.outbound.BaseTest.AWS_ACCESS_KEY;
import static io.camunda.connector.sns.outbound.BaseTest.AWS_SECRET_KEY;
import static io.camunda.connector.sns.outbound.BaseTest.AWS_TOPIC_ARN;
import static io.camunda.connector.sns.outbound.BaseTest.AWS_TOPIC_REGION;
import static io.camunda.connector.sns.outbound.BaseTest.MSG_ID;
import static io.camunda.connector.sns.outbound.BaseTest.objectMapper;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.sns.outbound.model.SnsConnectorRequest;
import io.camunda.connector.sns.outbound.model.SnsConnectorResult;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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

  private static Stream<SnsConnectorRequest> successRequestCases() throws IOException {
    return loadRequestCasesFromFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<SnsConnectorRequest> failRequestCases() throws IOException {
    return loadRequestCasesFromFile(FAIL_CASES_RESOURCE_PATH);
  }

  @SuppressWarnings("unchecked")
  private static Stream<SnsConnectorRequest> loadRequestCasesFromFile(final String fileName)
      throws IOException {
    final String cases = readString(new File(fileName).toPath(), UTF_8);
    return objectMapper
        .readValue(cases, new TypeReference<List<SnsConnectorRequest>>() {})
        .stream();
  }

  @BeforeEach
  void setup() {
    function =
        new SnsConnectorFunction(snsClientSupplier, ObjectMapperSupplier.getMapperInstance());
  }

  @ParameterizedTest
  @MethodSource("successRequestCases")
  void execute_ShouldSucceedSuccessCases(final SnsConnectorRequest expectedRequest)
      throws JsonProcessingException {
    // given
    when(snsClientSupplier.getSnsClient(any(AWSCredentialsProvider.class), eq(ACTUAL_TOPIC_REGION)))
        .thenReturn(snsClient);
    PublishResult publishResult = mock(PublishResult.class);
    when(publishResult.getMessageId()).thenReturn(MSG_ID);
    when(snsClient.publish(publishRequest.capture())).thenReturn(publishResult);
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(objectMapper.writeValueAsString(expectedRequest))
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_TOPIC_ARN, ACTUAL_TOPIC_ARN)
            .secret(AWS_TOPIC_REGION, ACTUAL_TOPIC_REGION)
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
  void execute_ShouldThrowExceptionOnMalformedRequests(final SnsConnectorRequest request)
      throws JsonProcessingException {
    // given
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(objectMapper.writeValueAsString(request))
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_TOPIC_ARN, ACTUAL_TOPIC_ARN)
            .secret(AWS_TOPIC_REGION, ACTUAL_TOPIC_REGION)
            .build();

    // when & then
    assertThrows(Exception.class, () -> function.execute(ctx));
  }
}
