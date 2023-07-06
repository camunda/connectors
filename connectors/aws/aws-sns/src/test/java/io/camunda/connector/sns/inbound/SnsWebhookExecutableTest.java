/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;
import com.amazonaws.services.sns.message.SnsSubscriptionConfirmation;
import com.amazonaws.services.sns.message.SnsUnknownMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnsWebhookExecutableTest {

  @Mock private InboundConnectorContext ctx;
  @Mock private ObjectMapper mapper;
  @Mock private SnsClientSupplier snsClientSupplier;
  @Mock private SnsMessageManager messageManager;
  private SnsWebhookExecutable testObject;

  private final Map<String, String> snsRequestHeaders =
      Map.of(
          "user-agent", "Amazon Simple Notification Service Agent",
          "content-type", "text/plain; charset=UTF-8",
          "x-amz-sns-topic-arn", "arn:aws:sns:eu-central-1:111222333444:SNSWebhook",
          "x-amz-sns-message-id", "b9b4574f-b4ab-4c03-ac14-a3145896747f");

  private static final String SUBSCRIPTION_CONFIRMATION_REQUEST =
      """
                  {
                    "Type": "SubscriptionConfirmation",
                    "MessageId": "b9b4574f-b4ab-4c03-ac14-a3145896747f",
                    "Token": "2336412f37fb687f5d51e6e2425c464de12884d217e7e1b25b4c24b4450f9aa05e48016238eefabedec9af616cb7ef5ce99b4971b74f7070b6375e42a1052f57240475072de0c1898fdf2871f4d5dadcecd5ac9f846e33de54818faf18d05560073594e7694509eb33acb0b6f806919b",
                    "TopicArn": "arn:aws:sns:eu-central-1:111222333444:SNSWebhook",
                    "Message": "You have chosen to subscribe to the topic arn:aws:sns:eu-central-1:111222333444:SNSWebhook.\\nTo confirm the subscription, visit the SubscribeURL included in this message.",
                    "SubscribeURL": "https://sns.eu-central-1.amazonaws.com/?Action=ConfirmSubscription&TopicArn=arn:aws:sns:eu-central-1:613365526843:SNSWebhook&Token=2336412f37fb687f5d51e6e2425c464de12884d217e7e1b25b4c24b4450f9aa05e48016238eefabedec9af616cb7ef5ce99b4971b74f7070b6375e42a1052f57240475072de0c1898fdf2871f4d5dadcecd5ac9f846e33de54818faf18d05560073594e7694509eb33acb0b6f806918b",
                    "Timestamp": "2023-04-26T15:04:47.883Z",
                    "SignatureVersion": "1",
                    "Signature": "u+0i/F/+qewEydLglZmwDwPBx7Kp1NuNfYwd8oLY7Wl0VJv5jGTC7BaJug019Rjebbkl2ykPcC2dEcgesjuPrTdPMBjiYqzpFWmToIdF32RhJCLZMZvaJsRHeIMqO4gRQVV3LRHo7eyiYzZ+hzkPldyl21buAgIjKUfv7Uz84nwNq7kG66m7TnuotqjYTp5zgvOYk++9Tk7K8PJeRXdnr+CMrL9ldctTK7gEoModQsCOXkvKQXfsAfy3bg0GC4G/Fk5hQyhLPy/SvBFjc+txHEr2AcYhoVQoxtiIKs2cRQiTpAVg6ImU0vC6uIQqftqjwo7kdth+Vl9itHufU3PSzw==",
                    "SigningCertURL": "https://sns.eu-central-1.amazonaws.com/SimpleNotificationService-56e67fcb41f6fec09b0196692625d385.pem"
                  }
          """;
  private static final String NOTIFICATION_REQUEST =
      """
                  {
                    "Type" : "Notification",
                    "MessageId" : "2e062e6b-a527-5e68-b69b-72a8e42add60",
                    "TopicArn" : "arn:aws:sns:eu-central-1:111222333444:SNSWebhook",
                    "Subject" : "Subject - test",
                    "Message" : "Hello, world",
                    "Timestamp" : "2023-04-26T15:10:05.479Z",
                    "SignatureVersion" : "1",
                    "Signature" : "a2wKUBFEsuTer/0lL6SP7UPxCNKN23p1g/6xfhvPKsYcY+1a3DFDtlpe9hPOQvz7Mcwws82jO1+UvT0UzWP6Sl4Xo0Soh6okAzItfUj2Etq4i8zmT0eQdgKZw7/EIn7RGTciIgc3vd2JkWqwZvO2WFMl0g8Cxxz5/gXzEEdopRPEI3/cOXLvRo4uRQv3txm3wNeG+Gx9mCAxNlBKL/DcjVu/AtskRgtLyaAvZBguGXbh8iaai2+q6iQp4NrsB/tb/9Hn7iBwjN/cTrcD1GQDtI29IwPeEOJbQpdcb5geoO3w3IYpIhDTC2MlzTUu4ERPIgngZ6I5EvM9JIM3nS1fjA==",
                    "SigningCertURL" : "https://sns.eu-central-1.amazonaws.com/SimpleNotificationService-56e67fcb41f6fec09b0196692625d385.pem",
                    "UnsubscribeURL" : "https://sns.eu-central-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-central-1:613365526843:SNSWebhook:4aa14ec3-a492-4a8e-8247-ea658d1aad96",
                    "MessageAttributes" : {
                      "attrName1" : {"Type":"String","Value":"attrVal"}
                    }
                  }
          """;

  @BeforeEach
  void beforeEach() {
    when(snsClientSupplier.messageManager(anyString())).thenReturn(messageManager);
    testObject = new SnsWebhookExecutable(mapper, snsClientSupplier);
  }

  @Test
  void triggerWebhook_SubscriptionAnyTopicAllowed_HappyCase() throws Exception {
    // Configure connector
    Map<String, Object> actualBPMNProperties =
        Map.of(
            "inbound",
            Map.of(
                "context", "snstest",
                "securitySubscriptionAllowedFor", "any"));

    when(ctx.getProperties()).thenReturn(actualBPMNProperties);

    // Configure payload
    final var headers = new HashMap<>(snsRequestHeaders);
    headers.put("x-amz-sns-message-type", "SubscriptionConfirmation");
    final var confirmation = mock(SnsSubscriptionConfirmation.class);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("GET");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody())
        .thenReturn(SUBSCRIPTION_CONFIRMATION_REQUEST.getBytes(StandardCharsets.UTF_8));

    when(messageManager.parseMessage(any())).thenReturn(confirmation);

    // when
    testObject.activate(ctx);
    final var result = testObject.triggerWebhook(payload);

    // then
    verify(confirmation).confirmSubscription();
    Assertions.assertThat(result.connectorData()).containsEntry("snsEventType", "Subscription");
  }

  @Test
  void triggerWebhook_SubscriptionAllowlistSingleTopic_HappyCase() throws Exception {
    // Configure connector
    Map<String, Object> actualBPMNProperties =
        Map.of(
            "inbound",
            Map.of(
                "context", "snstest",
                "securitySubscriptionAllowedFor", "specific",
                "topicsAllowList", "arn:aws:sns:eu-central-1:111222333444:SNSWebhook"));

    when(ctx.getProperties()).thenReturn(actualBPMNProperties);

    // Configure payload
    final var headers = new HashMap<>(snsRequestHeaders);
    headers.put("x-amz-sns-message-type", "SubscriptionConfirmation");
    final var confirmation = mock(SnsSubscriptionConfirmation.class);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("GET");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody())
        .thenReturn(SUBSCRIPTION_CONFIRMATION_REQUEST.getBytes(StandardCharsets.UTF_8));

    when(messageManager.parseMessage(any())).thenReturn(confirmation);

    // when
    testObject.activate(ctx);
    final var result = testObject.triggerWebhook(payload);

    // then
    verify(confirmation).confirmSubscription();
    Assertions.assertThat(result.connectorData()).containsEntry("snsEventType", "Subscription");
  }

  @Test
  void triggerWebhook_SubscriptionAllowlistMultipleTopics_HappyCase() throws Exception {
    // Configure connector
    Map<String, Object> actualBPMNProperties =
        Map.of(
            "inbound",
            Map.of(
                "context",
                "snstest",
                "securitySubscriptionAllowedFor",
                "specific",
                "topicsAllowList",
                "arn:aws:sns:eu-central-1:111222333444:SNSWebhook, arn:aws:sns:eu-central-1:111222333444:AnotherTopic"));

    when(ctx.getProperties()).thenReturn(actualBPMNProperties);

    // Configure payload
    final var headers = new HashMap<>(snsRequestHeaders);
    headers.put("x-amz-sns-message-type", "SubscriptionConfirmation");
    final var confirmation = mock(SnsSubscriptionConfirmation.class);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("GET");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody())
        .thenReturn(SUBSCRIPTION_CONFIRMATION_REQUEST.getBytes(StandardCharsets.UTF_8));

    when(messageManager.parseMessage(any())).thenReturn(confirmation);

    // when
    testObject.activate(ctx);
    final var result = testObject.triggerWebhook(payload);

    // then
    verify(confirmation).confirmSubscription();
    Assertions.assertThat(result.connectorData()).containsEntry("snsEventType", "Subscription");
  }

  @Test
  void triggerWebhook_SubscriptionNoAllowlistTopic_RaiseException() throws Exception {
    // Configure connector
    Map<String, Object> actualBPMNProperties =
        Map.of(
            "inbound",
            Map.of(
                "context", "snstest",
                "securitySubscriptionAllowedFor", "specific",
                "topicsAllowList", "arn:aws:sns:eu-central-1:111222333444:WrongTopic"));

    when(ctx.getProperties()).thenReturn(actualBPMNProperties);

    // Configure payload
    final var headers = new HashMap<>(snsRequestHeaders);
    headers.put("x-amz-sns-message-type", "SubscriptionConfirmation");
    final var confirmation = mock(SnsSubscriptionConfirmation.class);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("GET");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody())
        .thenReturn(SUBSCRIPTION_CONFIRMATION_REQUEST.getBytes(StandardCharsets.UTF_8));

    when(messageManager.parseMessage(any())).thenReturn(confirmation);

    // when & then
    testObject.activate(ctx);
    Assert.assertThrows(Exception.class, () -> testObject.triggerWebhook(payload));
  }

  @Test
  void triggerWebhook_SubscriptionAllowListEmpty_RaiseException() throws Exception {
    // Configure connector
    Map<String, Object> actualBPMNProperties =
        Map.of(
            "inbound",
            Map.of(
                "context", "snstest",
                "securitySubscriptionAllowedFor", "specific"));

    when(ctx.getProperties()).thenReturn(actualBPMNProperties);

    // Configure payload
    final var headers = new HashMap<>(snsRequestHeaders);
    headers.put("x-amz-sns-message-type", "SubscriptionConfirmation");
    final var confirmation = mock(SnsSubscriptionConfirmation.class);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("GET");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody())
        .thenReturn(SUBSCRIPTION_CONFIRMATION_REQUEST.getBytes(StandardCharsets.UTF_8));

    when(messageManager.parseMessage(any())).thenReturn(confirmation);

    // when & then
    testObject.activate(ctx);
    Assert.assertThrows(Exception.class, () -> testObject.triggerWebhook(payload));
  }

  @Test
  void triggerWebhook_Notification_HappyCase() throws Exception {
    // Configure connector
    Map<String, Object> actualBPMNProperties =
        Map.of(
            "inbound",
            Map.of(
                "context", "snstest",
                "securitySubscriptionAllowedFor", "any"));

    when(ctx.getProperties()).thenReturn(actualBPMNProperties);

    // Configure payload
    final var headers = new HashMap<>(snsRequestHeaders);
    headers.put("x-amz-sns-message-type", "Notification");
    final var notification = mock(SnsNotification.class);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("GET");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(NOTIFICATION_REQUEST.getBytes(StandardCharsets.UTF_8));

    when(messageManager.parseMessage(any())).thenReturn(notification);

    // when
    testObject.activate(ctx);
    final var result = testObject.triggerWebhook(payload);

    // then
    Assertions.assertThat(result.connectorData()).containsEntry("snsEventType", "Notification");
  }

  @Test
  void triggerWebhook_UnknownMessage_ThrowsException() throws Exception {
    // Configure connector
    Map<String, Object> actualBPMNProperties =
        Map.of(
            "inbound",
            Map.of(
                "context", "snstest",
                "securitySubscriptionAllowedFor", "any"));

    when(ctx.getProperties()).thenReturn(actualBPMNProperties);

    // Configure payload
    final var headers = new HashMap<>(snsRequestHeaders);
    headers.put("x-amz-sns-message-type", "CorruptedNotification");
    final var unknownMessage = mock(SnsUnknownMessage.class);
    final var payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("GET");
    when(payload.headers()).thenReturn(headers);
    when(payload.rawBody()).thenReturn(NOTIFICATION_REQUEST.getBytes(StandardCharsets.UTF_8));

    when(messageManager.parseMessage(any())).thenReturn(unknownMessage);

    // when & then
    testObject.activate(ctx);
    Assert.assertThrows(Exception.class, () -> testObject.triggerWebhook(payload));
  }
}
