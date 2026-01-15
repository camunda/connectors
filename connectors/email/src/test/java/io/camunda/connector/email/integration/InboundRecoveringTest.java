/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.integration;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.inbound.JakartaEmailListener;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.inbound.model.EmailInboundConnectorProperties;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import io.camunda.connector.email.inbound.model.HandlingStrategy;
import io.camunda.connector.email.inbound.model.PollUnseen;
import io.camunda.connector.email.response.ReadEmailResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class InboundRecoveringTest extends BaseEmailTest {

  private final int PROXY_PORT = 22223;
  JakartaEmailListener jakartaEmailListener = new JakartaEmailListener();

  @BeforeEach
  public void beforeEach() {
    Mockito.clearAllCaches();
    super.reset();
  }

  @AfterEach
  public void afterEach() {
    jakartaEmailListener.stopListener();
  }

  @Test
  public void pollingManagerBreaksAndRecoverAfterServerNotResponding() {
    try (ImapServerProxy proxyImap =
        new ImapServerProxy(
            PROXY_PORT, "localhost", Integer.parseInt(super.getUnsecureImapPort()))) {
      InboundConnectorContext inboundConnectorContext = mock(InboundConnectorContext.class);

      EmailInboundConnectorProperties emailInboundConnectorProperties =
          new EmailInboundConnectorProperties(
              new SimpleAuthentication("test@camunda.com", "password"),
              new EmailListenerConfig(
                  new ImapConfig("localhost", PROXY_PORT, CryptographicProtocol.NONE),
                  "",
                  Duration.of(1, ChronoUnit.SECONDS),
                  new PollUnseen(HandlingStrategy.READ, "")));

      doNothing().when(inboundConnectorContext).log(any(Consumer.class));
      when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
          .thenReturn(emailInboundConnectorProperties);
      when(inboundConnectorContext.canActivate(any()))
          .thenReturn(new ActivationCheckResult.Success.CanActivate(null));
      when(inboundConnectorContext.correlate(any()))
          .thenReturn(new CorrelationResult.Success.ProcessInstanceCreated(null, null, null));

      proxyImap.setOkProxy();
      this.jakartaEmailListener.startListener(inboundConnectorContext);

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  // We want to check it was called once at startup and once at poll
                  verify(inboundConnectorContext, atLeast(2))
                      .reportHealth(argThat(health -> health.getStatus() == Health.Status.UP)));

      // It will make the proxy switch to a kill mode, will send BYE all the time to the server,
      // closing the connection
      proxyImap.cutConnection();

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  // We want to check health was down for at least 1 times
                  verify(inboundConnectorContext, atLeast(1))
                      .reportHealth(argThat(health -> health.getStatus() == Health.Status.DOWN)));

      Mockito.clearInvocations(inboundConnectorContext);
      proxyImap.setOkProxy();

      super.sendEmail("camunda@test.com", "Subject", "Content");

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  verify(inboundConnectorContext, atLeast(1))
                      .correlate(
                          argThat(
                              correlationRequest -> {
                                ReadEmailResponse response =
                                    ((ReadEmailResponse) correlationRequest.getVariables());
                                return response.plainTextBody().equals("Content")
                                    && response.subject().equals("Subject");
                              })));

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  verify(inboundConnectorContext, atLeast(1))
                      .reportHealth(argThat(health -> health.getStatus() == Health.Status.UP)));

    } catch (Exception e) {
      if (e instanceof IllegalStateException
          && e.getCause().getCause() instanceof java.net.SocketException) {
        // This exception is expected because the proxy may cut the connection while the client
        // is using it
        return;
      }
      throw new RuntimeException(e);
    }
  }

  @Test
  public void pollingManagerBreaksAndRecoverAfterRuntimeErrorFromTheRuntime() {
    try (ImapServerProxy proxyImap =
        new ImapServerProxy(
            PROXY_PORT, "localhost", Integer.parseInt(super.getUnsecureImapPort()))) {
      InboundConnectorContext inboundConnectorContext = mock(InboundConnectorContext.class);

      EmailInboundConnectorProperties emailInboundConnectorProperties =
          new EmailInboundConnectorProperties(
              new SimpleAuthentication("test@camunda.com", "password"),
              new EmailListenerConfig(
                  new ImapConfig("localhost", PROXY_PORT, CryptographicProtocol.NONE),
                  "",
                  Duration.of(1, ChronoUnit.SECONDS),
                  new PollUnseen(HandlingStrategy.READ, "")));

      doNothing().when(inboundConnectorContext).log(any(Consumer.class));
      when(inboundConnectorContext.bindProperties(EmailInboundConnectorProperties.class))
          .thenReturn(emailInboundConnectorProperties);
      when(inboundConnectorContext.canActivate(any()))
          .thenReturn(new ActivationCheckResult.Success.CanActivate(null));
      doThrow(new RuntimeException()).when(inboundConnectorContext).correlate(any());

      proxyImap.setOkProxy();
      this.jakartaEmailListener.startListener(inboundConnectorContext);

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  // We want to check it was called once at startup and once at poll
                  verify(inboundConnectorContext, atLeast(2))
                      .reportHealth(argThat(health -> health.getStatus() == Health.Status.UP)));

      super.sendEmail("camunda@test.com", "Subject", "Content");

      await()
          .atMost(10, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  // We want to check health was down for at least 1 times
                  verify(inboundConnectorContext, atLeast(1))
                      .reportHealth(argThat(health -> health.getStatus() == Health.Status.DOWN)));

      Mockito.clearInvocations(inboundConnectorContext);

      doReturn(new CorrelationResult.Success.ProcessInstanceCreated(null, null, null))
          .when(inboundConnectorContext)
          .correlate(any());

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  verify(inboundConnectorContext, atLeast(1))
                      .correlate(
                          argThat(
                              correlationRequest -> {
                                ReadEmailResponse response =
                                    ((ReadEmailResponse) correlationRequest.getVariables());
                                return response.plainTextBody().equals("Content")
                                    && response.subject().equals("Subject");
                              })));

      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  verify(inboundConnectorContext, atLeast(1))
                      .reportHealth(argThat(health -> health.getStatus() == Health.Status.UP)));

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
