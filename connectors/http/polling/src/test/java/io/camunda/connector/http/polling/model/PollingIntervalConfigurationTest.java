/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDetails;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PollingIntervalConfigurationTest {

  private InboundConnectorContextImpl inboundConnectorContext;
  private Map<String, String> properties;

  @Mock
  private InboundConnectorDetails connectorData; // Initialize or mock the connector definition

  @BeforeEach
  public void setUp() {
    SecretProvider secretProvider = name -> name; // Simplified secret provider for testing purposes
    properties = new HashMap<>();
    when(connectorData.rawPropertiesWithoutKeywords()).thenReturn(properties);
    inboundConnectorContext =
        new InboundConnectorContextImpl(
            secretProvider,
            (e) -> {},
            connectorData,
            null,
            (e) -> {},
            ConnectorsObjectMapperSupplier.getCopy(),
            EvictingQueue.create(10));
  }

  @ParameterizedTest
  @MethodSource("httpRequestIntervalTestCases")
  public void testGetHttpRequestInterval(String value, long expected) {
    inboundConnectorContext.getProperties().put("httpRequestInterval", value);
    PollingIntervalConfiguration intervals =
        inboundConnectorContext.bindProperties(PollingIntervalConfiguration.class);
    long interval = intervals.getHttpRequestInterval().toMillis();
    assertThat(interval).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("operatePollingIntervalTestCases")
  public void testGetOperatePollingInterval(String value, long expected) {
    inboundConnectorContext.getProperties().put("operatePollingInterval", value);
    PollingIntervalConfiguration intervals =
        inboundConnectorContext.bindProperties(PollingIntervalConfiguration.class);
    long interval = intervals.getOperatePollingInterval().toMillis();
    assertThat(interval).isEqualTo(expected);
  }

  private static Stream<Arguments> httpRequestIntervalTestCases() {
    return Stream.of(
        Arguments.of("PT3M", 180000L),
        Arguments.of("P1D", 86400000L),
        Arguments.of("PT1H30M10.5S", 5410500L),
        Arguments.of(null, 50000));
  }

  private static Stream<Arguments> operatePollingIntervalTestCases() {
    return Stream.of(
        Arguments.of("PT1M", 60000L),
        Arguments.of("PT45S", 45000L),
        Arguments.of("PT2H", 7200000L),
        Arguments.of("P1DT12H", 129600000L),
        Arguments.of(null, 5000));
  }
}
