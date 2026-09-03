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
package io.camunda.connector.runtime.outbound.jobhandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.ConnectorJobHandler;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The runtime log is where a connector failure would otherwise be reported unredacted, so it is
 * asserted on here, in the module that has a logging backend on its test classpath.
 */
class SpringConnectorJobHandlerTest {

  private static final String INPUT_DECLARING_A_SECRET = "{ \"token\" : \"{{secrets.FOO}}\" }";

  private record TokenHolder(String token) {}

  @Test
  void aSecretThatRotatedBetweenBindAndMaskingIsNotLoggedFromTheCause() {
    // given a secret that rotates between the job binding it and the masking re-read
    var jobHandler = connectorThrowingTheBoundToken("old-value", "new-value");
    var logger = (Logger) LoggerFactory.getLogger(ConnectorJobHandler.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    // when the job fails with the bound value in its message
    try {
      jobHandler.handle(mock(JobClient.class, RETURNS_DEEP_STUBS), jobDeclaringASecret());
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    // then the runtime log carries neither the value nor the unredacted cause it came from
    assertThat(appender.list)
        .filteredOn(event -> event.getFormattedMessage().startsWith("Exception while processing"))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getFormattedMessage()).doesNotContain("old-value");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  private SpringConnectorJobHandler connectorThrowingTheBoundToken(
      String boundValue, String rotatedValue) {
    var callCount = new AtomicInteger();
    SecretProvider rotatingSecretProvider =
        name -> callCount.getAndIncrement() == 0 ? boundValue : rotatedValue;
    return new SpringConnectorJobHandler(
        immediateMetricsRecorder(),
        mock(CommandExceptionHandlingStrategy.class),
        new SecretProviderAggregator(List.of(rotatingSecretProvider)),
        input -> {},
        ConnectorsObjectMapperSupplier.getCopy(),
        context -> {
          var bound = context.bindVariables(TokenHolder.class);
          throw new RuntimeException("api rejected " + bound.token());
        },
        new OutboundConnectorConfiguration("test", new String[0], "io.camunda:test:1", null),
        SecretFilterFactory.disabled());
  }

  private static MetricsRecorder immediateMetricsRecorder() {
    var metricsRecorder = mock(MetricsRecorder.class);
    doAnswer(
            invocation -> {
              invocation.getArgument(2, Runnable.class).run();
              return null;
            })
        .when(metricsRecorder)
        .executeWithTimer(any(), any(), any());
    return metricsRecorder;
  }

  private static ActivatedJob jobDeclaringASecret() {
    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn(INPUT_DECLARING_A_SECRET);
    when(job.getCustomHeaders()).thenReturn(Map.of());
    when(job.getRetries()).thenReturn(3);
    return job;
  }
}
