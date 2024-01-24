package io.camunda.connector.runtime.inbound;

import static io.camunda.connector.runtime.inbound.WebhookControllerPlainJavaTests.buildConnector;
import static io.camunda.connector.runtime.inbound.WebhookControllerPlainJavaTests.webhookDefinition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.runtime.inbound.lifecycle.ActiveInboundConnector;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WebhookControllerLogTest {
  @Captor ArgumentCaptor<Activity> activityCaptor;

  private static Stream<Arguments> invalidCases() {
    return Stream.of(
        Arguments.of("&20encoded+whitespace"),
        Arguments.of("€"),
        Arguments.of("-my-path"),
        Arguments.of("my_path_"));
  }

  @ParameterizedTest
  @MethodSource("invalidCases")
  public void webhookPathLogIfInvalidCharacters(String webhookPath) {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector(webhookDefinition("processA", 1, webhookPath));

    // when
    webhook.register(processA1);

    // then
    verify(processA1.context(), times(1)).log(activityCaptor.capture());
    Activity passedActivity = activityCaptor.getValue();
    assertEquals(Severity.WARNING, passedActivity.severity());
    assertTrue(passedActivity.message().contains(webhookPath));

    assertWebhookRegistered(webhookPath, webhook, processA1);
  }

  private static Stream<Arguments> validCases() {
    return Stream.of(
        Arguments.of("z"),
        Arguments.of("validAlphaOnly"),
        Arguments.of("hello-world"),
        Arguments.of("123-456_789"));
  }

  @ParameterizedTest
  @MethodSource("validCases")
  public void webhookPathDontLogValidCharacters(String webhookPath) {
    WebhookConnectorRegistry webhook = new WebhookConnectorRegistry();

    // given
    var processA1 = buildConnector(webhookDefinition("processA", 1, webhookPath));

    // when
    webhook.register(processA1);

    // then
    verify(processA1.context(), times(0)).log(activityCaptor.capture());
    assertWebhookRegistered(webhookPath, webhook, processA1);
  }

  private static void assertWebhookRegistered(
      String webhookPath, WebhookConnectorRegistry webhook, ActiveInboundConnector processA1) {
    var existingConnector = webhook.getWebhookConnectorByContextPath(webhookPath);
    assertTrue(existingConnector.isPresent());
    assertEquals(processA1, existingConnector.get());
  }
}
