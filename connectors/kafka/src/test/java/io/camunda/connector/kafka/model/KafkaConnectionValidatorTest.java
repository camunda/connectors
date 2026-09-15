/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.ClusterAuthorizationException;
import org.apache.kafka.common.errors.DisconnectException;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.errors.SslAuthenticationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class KafkaConnectionValidatorTest {

  private static final KafkaConnectionConfiguration VALID =
      new KafkaConnectionConfiguration("broker.example.com:9093", "the-login", "the-secret");
  private static final Duration TIMEOUT = Duration.ofMillis(125);
  private static final String SENSITIVE_DETAIL = "the-login the-secret broker.example.com:9093";

  private final Admin admin = mock(Admin.class);

  @SuppressWarnings("unchecked")
  private final Function<Properties, Admin> factory = mock(Function.class);

  @SuppressWarnings("unchecked")
  private final KafkaFuture<Collection<Node>> nodes = mock(KafkaFuture.class);

  private final KafkaConnectionValidator validator = new KafkaConnectionValidator(factory, TIMEOUT);

  @Test
  void describesTheClusterWithOnlyAuthenticationBootstrapAndTimeoutProperties() throws Exception {
    prepareProbe();
    when(nodes.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
        .thenReturn(List.of(new Node(1, "broker.example.com", 9093)));

    assertThat(validator.validate(VALID)).isEqualTo(ConfigurationValidationResult.success());

    var properties = ArgumentCaptor.forClass(Properties.class);
    verify(factory).apply(properties.capture());
    var expected = KafkaPropertiesUtil.produceAuthenticationProperties(VALID.toAuthentication());
    expected.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, VALID.bootstrapServers());
    expected.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 125);
    expected.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 125);
    assertThat(properties.getValue()).isEqualTo(expected);
    var options = ArgumentCaptor.forClass(DescribeClusterOptions.class);
    verify(admin).describeCluster(options.capture());
    assertThat(options.getValue().includeAuthorizedOperations()).isFalse();
    assertThat(options.getValue().timeoutMs()).isEqualTo(125);
    verify(nodes).get(125, TimeUnit.MILLISECONDS);
    verify(admin).close(TIMEOUT);
    verifyNoMoreInteractions(admin, nodes);
  }

  @ParameterizedTest
  @MethodSource("probeFailures")
  void classifiesFailuresWithoutExposingDetails(Throwable failure, String expectedCode)
      throws Exception {
    prepareProbe();
    when(nodes.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
        .thenThrow(new ExecutionException(failure));

    assertSafeFailure(validator.validate(VALID), expectedCode);
    verify(admin).close(TIMEOUT);
  }

  private static Stream<Arguments> probeFailures() {
    return Stream.of(
        Arguments.of(new AuthenticationException(SENSITIVE_DETAIL), "UNAUTHORIZED"),
        Arguments.of(new SaslAuthenticationException(SENSITIVE_DETAIL), "UNAUTHORIZED"),
        Arguments.of(
            new KafkaException(SENSITIVE_DETAIL, new SaslAuthenticationException(SENSITIVE_DETAIL)),
            "UNAUTHORIZED"),
        Arguments.of(new SslAuthenticationException(SENSITIVE_DETAIL), "ERROR"),
        Arguments.of(new ClusterAuthorizationException(SENSITIVE_DETAIL), "ERROR"),
        Arguments.of(new DisconnectException(SENSITIVE_DETAIL), "ERROR"),
        Arguments.of(
            new org.apache.kafka.common.errors.TimeoutException(SENSITIVE_DETAIL), "ERROR"),
        Arguments.of(new KafkaException(SENSITIVE_DETAIL), "ERROR"),
        Arguments.of(new ConfigException(SENSITIVE_DETAIL), "INVALID_INPUT"),
        Arguments.of(new IllegalArgumentException(SENSITIVE_DETAIL), "INVALID_INPUT"));
  }

  @ParameterizedTest
  @MethodSource("clientCreationFailures")
  void classifiesClientCreationFailures(RuntimeException failure, String expectedCode) {
    when(factory.apply(any())).thenThrow(failure);

    assertSafeFailure(validator.validate(VALID), expectedCode);
    verifyNoInteractions(admin);
  }

  private static Stream<Arguments> clientCreationFailures() {
    return Stream.of(
        Arguments.of(new ConfigException(SENSITIVE_DETAIL), "INVALID_INPUT"),
        Arguments.of(
            new KafkaException(SENSITIVE_DETAIL, new ConfigException(SENSITIVE_DETAIL)),
            "INVALID_INPUT"),
        Arguments.of(new IllegalArgumentException(SENSITIVE_DETAIL), "INVALID_INPUT"),
        Arguments.of(new SaslAuthenticationException(SENSITIVE_DETAIL), "UNAUTHORIZED"),
        Arguments.of(new KafkaException(SENSITIVE_DETAIL), "ERROR"));
  }

  @Test
  void timesOutTheFutureAndClosesTheClient() throws Exception {
    prepareProbe();
    when(nodes.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
        .thenThrow(new TimeoutException(SENSITIVE_DETAIL));

    assertSafeFailure(validator.validate(VALID), "ERROR");
    verify(admin).close(TIMEOUT);
  }

  @Test
  void handlesACancelledFuture() throws Exception {
    prepareProbe();
    when(nodes.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
        .thenThrow(new CancellationException(SENSITIVE_DETAIL));

    assertSafeFailure(validator.validate(VALID), "ERROR");
    verify(admin).close(TIMEOUT);
  }

  @Test
  void preservesInterruptionAndClosesTheClient() throws Exception {
    prepareProbe();
    when(nodes.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
        .thenThrow(new InterruptedException(SENSITIVE_DETAIL));

    try {
      assertSafeFailure(validator.validate(VALID), "ERROR");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verify(admin).close(TIMEOUT);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void preservesKafkaInterruption() {
    when(factory.apply(any())).thenThrow(new InterruptException(SENSITIVE_DETAIL));

    try {
      assertSafeFailure(validator.validate(VALID), "ERROR");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void reportsACloseFailureWithoutLeakingDetails() throws Exception {
    prepareProbe();
    when(nodes.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
        .thenReturn(List.of(new Node(1, "broker.example.com", 9093)));
    doThrow(new KafkaException(SENSITIVE_DETAIL)).when(admin).close(TIMEOUT);

    assertSafeFailure(validator.validate(VALID), "ERROR");
    verify(admin).close(TIMEOUT);
  }

  @Test
  void preservesTheProbeFailureWhenCloseAlsoFails() throws Exception {
    prepareProbe();
    when(nodes.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
        .thenThrow(new ExecutionException(new SaslAuthenticationException(SENSITIVE_DETAIL)));
    doThrow(new KafkaException(SENSITIVE_DETAIL)).when(admin).close(TIMEOUT);

    assertSafeFailure(validator.validate(VALID), "UNAUTHORIZED");
  }

  @Test
  void closesTheClientWhenAnUnexpectedFailurePropagates() {
    when(factory.apply(any())).thenReturn(admin);
    var failure = new IllegalStateException(SENSITIVE_DETAIL);
    when(admin.describeCluster(any(DescribeClusterOptions.class))).thenThrow(failure);

    assertThatThrownBy(() -> validator.validate(VALID)).isSameAs(failure);
    verify(admin).close(TIMEOUT);
  }

  @Test
  void rejectsANullConfiguration() {
    assertSafeFailure(validator.validate(null), "INVALID_INPUT");
    verifyNoInteractions(factory, admin);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rejectsMissingValuesWithoutCreatingAClient(String blank) {
    assertSafeFailure(
        validator.validate(
            new KafkaConnectionConfiguration(blank, VALID.username(), VALID.password())),
        "INVALID_INPUT");
    assertSafeFailure(
        validator.validate(
            new KafkaConnectionConfiguration(VALID.bootstrapServers(), blank, VALID.password())),
        "INVALID_INPUT");
    assertSafeFailure(
        validator.validate(
            new KafkaConnectionConfiguration(VALID.bootstrapServers(), VALID.username(), blank)),
        "INVALID_INPUT");
    verifyNoInteractions(factory, admin);
  }

  @Test
  @SuppressWarnings("rawtypes")
  void isDiscoverableViaTheServiceLoader() {
    assertThat(
            ServiceLoader.load(ConfigurationValidator.class).stream()
                .filter(provider -> provider.type().equals(KafkaConnectionValidator.class))
                .map(ServiceLoader.Provider::get))
        .singleElement()
        .isInstanceOf(KafkaConnectionValidator.class);
  }

  private void prepareProbe() {
    when(factory.apply(any())).thenReturn(admin);
    var cluster = mock(DescribeClusterResult.class);
    when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
    when(cluster.nodes()).thenReturn(nodes);
  }

  private static void assertSafeFailure(ConfigurationValidationResult result, String expectedCode) {
    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(expectedCode);
    assertThat(result.message())
        .isEqualTo(
            switch (expectedCode) {
              case "INVALID_INPUT" -> KafkaConnectionValidator.INVALID_INPUT_MESSAGE;
              case "UNAUTHORIZED" -> KafkaConnectionValidator.UNAUTHORIZED_MESSAGE;
              default -> KafkaConnectionValidator.ERROR_MESSAGE;
            })
        .doesNotContain(VALID.bootstrapServers(), VALID.username(), VALID.password());
  }
}
