/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;
import com.amazonaws.services.sns.message.SnsSubscriptionConfirmation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Golden-JSON shape tests for the SNS inbound webhook result.
 *
 * <p>{@link SnsWebhookExecutable} still relies on the AWS SDK v1 {@code SnsMessageManager} to
 * parse/verify the HTTPS webhook body (there is no v2 equivalent, see <a
 * href="https://github.com/aws/aws-sdk-java-v2/issues/1302">aws-sdk-java-v2#1302</a>) and will keep
 * doing so after the connector's v1 -&gt; v2 migration (issue #7974). What must not change silently
 * is the JSON this connector writes to process variables for correlation: this test pins that shape
 * exactly as produced TODAY, so it stays green, unchanged, throughout the migration (part of
 * #7968).
 *
 * <p>Unlike {@link SnsWebhookExecutableTest}, which mocks the internal {@link ObjectMapper} (so
 * {@code bodyAsMap} is always {@code null} there), these tests wire in the REAL production mapper
 * so the webhook body is actually parsed into the map the connector puts on the result, exactly as
 * it happens at runtime.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnsWebhookResultShapeTest {

  private static final String TOPIC_ARN = "arn:aws:sns:eu-central-1:111222333444:SNSWebhook";

  // Sample SNS "Notification" HTTPS callback body, reused from SnsWebhookExecutableTest.
  private static final String NOTIFICATION_BODY =
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

  // Sample SNS "SubscriptionConfirmation" HTTPS callback body, reused from
  // SnsWebhookExecutableTest.
  private static final String SUBSCRIPTION_CONFIRMATION_BODY =
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

  @Mock private SnsClientSupplier snsClientSupplier;
  @Mock private SnsMessageManager messageManager;

  private final ObjectMapper productionMapper = ObjectMapperSupplier.getMapperInstance();
  private SnsWebhookExecutable executable;

  @BeforeEach
  void setUp() {
    when(snsClientSupplier.messageManager(anyString())).thenReturn(messageManager);
    executable = new SnsWebhookExecutable(productionMapper, snsClientSupplier);
  }

  /**
   * Golden-JSON shape test: the {@link WebhookResult} a live SNS "Notification" callback produces
   * today, serialized with the real connectors {@link ObjectMapper}, must reproduce the output
   * contract the connector documented before the AWS SDK v1 -&gt; v2 migration.
   */
  @Test
  void notification_serializesToDocumentedV1JsonShape() throws Exception {
    // Given an activated connector and a realistic SNS "Notification" HTTPS callback: the headers
    // AWS SNS sets on every request, and a fully populated body (subject, message, unsubscribe
    // URL, signature/cert metadata, one message attribute).
    activateAllowingAnyTopic();

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("user-agent", "Amazon Simple Notification Service Agent");
    headers.put("content-type", "text/plain; charset=UTF-8");
    headers.put("x-amz-sns-message-type", "Notification");
    headers.put("x-amz-sns-topic-arn", TOPIC_ARN);
    headers.put("x-amz-sns-message-id", "2e062e6b-a527-5e68-b69b-72a8e42add60");

    WebhookProcessingPayload payload = payloadWith(NOTIFICATION_BODY, headers);
    when(messageManager.parseMessage(any())).thenReturn(mock(SnsNotification.class));

    // When the webhook is triggered, exactly as it would be for a live SNS Notification callback
    WebhookResult result = executable.triggerWebhook(payload);

    // Then the result (parsed body + headers + params, and the "snsEventType" correlation
    // discriminator) serializes to the documented v1 shape exactly, including the full raw SNS
    // envelope. Note this is pinning today's (arguably surprising) behavior: the Signature and
    // SigningCertURL fields flow straight through into the correlated process variables,
    // unfiltered, because the connector parses the whole HTTP body into a generic Map - this is
    // intentional v1 behavior being preserved for the migration, not a design endorsement.
    JsonNode actual = productionMapper.valueToTree(result);
    String expectedJson =
        """
        {
          "request": {
            "body": {
              "Type": "Notification",
              "MessageId": "2e062e6b-a527-5e68-b69b-72a8e42add60",
              "TopicArn": "arn:aws:sns:eu-central-1:111222333444:SNSWebhook",
              "Subject": "Subject - test",
              "Message": "Hello, world",
              "Timestamp": "2023-04-26T15:10:05.479Z",
              "SignatureVersion": "1",
              "Signature": "a2wKUBFEsuTer/0lL6SP7UPxCNKN23p1g/6xfhvPKsYcY+1a3DFDtlpe9hPOQvz7Mcwws82jO1+UvT0UzWP6Sl4Xo0Soh6okAzItfUj2Etq4i8zmT0eQdgKZw7/EIn7RGTciIgc3vd2JkWqwZvO2WFMl0g8Cxxz5/gXzEEdopRPEI3/cOXLvRo4uRQv3txm3wNeG+Gx9mCAxNlBKL/DcjVu/AtskRgtLyaAvZBguGXbh8iaai2+q6iQp4NrsB/tb/9Hn7iBwjN/cTrcD1GQDtI29IwPeEOJbQpdcb5geoO3w3IYpIhDTC2MlzTUu4ERPIgngZ6I5EvM9JIM3nS1fjA==",
              "SigningCertURL": "https://sns.eu-central-1.amazonaws.com/SimpleNotificationService-56e67fcb41f6fec09b0196692625d385.pem",
              "UnsubscribeURL": "https://sns.eu-central-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-central-1:613365526843:SNSWebhook:4aa14ec3-a492-4a8e-8247-ea658d1aad96",
              "MessageAttributes": {
                "attrName1": {"Type": "String", "Value": "attrVal"}
              }
            },
            "headers": {
              "user-agent": "Amazon Simple Notification Service Agent",
              "content-type": "text/plain; charset=UTF-8",
              "x-amz-sns-message-type": "Notification",
              "x-amz-sns-topic-arn": "arn:aws:sns:eu-central-1:111222333444:SNSWebhook",
              "x-amz-sns-message-id": "2e062e6b-a527-5e68-b69b-72a8e42add60"
            },
            "params": {}
          },
          "connectorData": {
            "snsEventType": "Notification"
          }
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Tree equality above is key-order-insensitive; also pin the serialized field order to the
    // documented v1 layout (request before connectorData; body/headers/params in that order).
    assertThat(productionMapper.writeValueAsString(result))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  /**
   * Golden-JSON shape test for the other branch of the result path: a live SNS
   * "SubscriptionConfirmation" callback produces the exact same {@link
   * io.camunda.connector.sns.inbound.model.SnsWebhookProcessingResult} shape as a Notification
   * (request + connectorData) - only the "snsEventType" discriminator value and the body/header
   * content differ. This is intentional v1 behavior: {@code tryConfirmSubscription} and {@code
   * handleNotification} both build the same result record.
   */
  @Test
  void subscriptionConfirmation_serializesToDocumentedV1JsonShape() throws Exception {
    // Given an activated connector and a realistic SNS "SubscriptionConfirmation" HTTPS callback.
    activateAllowingAnyTopic();

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("user-agent", "Amazon Simple Notification Service Agent");
    headers.put("content-type", "text/plain; charset=UTF-8");
    headers.put("x-amz-sns-message-type", "SubscriptionConfirmation");
    headers.put("x-amz-sns-topic-arn", TOPIC_ARN);
    headers.put("x-amz-sns-message-id", "b9b4574f-b4ab-4c03-ac14-a3145896747f");

    WebhookProcessingPayload payload = payloadWith(SUBSCRIPTION_CONFIRMATION_BODY, headers);
    SnsSubscriptionConfirmation confirmation = mock(SnsSubscriptionConfirmation.class);
    when(messageManager.parseMessage(any())).thenReturn(confirmation);

    // When the webhook is triggered, exactly as it would be for a live SNS SubscriptionConfirmation
    // callback
    WebhookResult result = executable.triggerWebhook(payload);

    // Then the connector confirmed the subscription as a side effect...
    verify(confirmation).confirmSubscription();
    // ...and the result serializes to the documented v1 shape exactly. As with Notification, the
    // raw SNS envelope - including the confirmation Token and SubscribeURL - flows straight
    // through into the correlated process variables, unfiltered; this is intentional v1 behavior
    // being pinned for the migration, not a design endorsement.
    JsonNode actual = productionMapper.valueToTree(result);
    String expectedJson =
        """
        {
          "request": {
            "body": {
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
            },
            "headers": {
              "user-agent": "Amazon Simple Notification Service Agent",
              "content-type": "text/plain; charset=UTF-8",
              "x-amz-sns-message-type": "SubscriptionConfirmation",
              "x-amz-sns-topic-arn": "arn:aws:sns:eu-central-1:111222333444:SNSWebhook",
              "x-amz-sns-message-id": "b9b4574f-b4ab-4c03-ac14-a3145896747f"
            },
            "params": {}
          },
          "connectorData": {
            "snsEventType": "Subscription"
          }
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    assertThat(productionMapper.writeValueAsString(result))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  private void activateAllowingAnyTopic() throws Exception {
    InboundConnectorContext ctx =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "inbound",
                    Map.of(
                        "context", "snstest",
                        "securitySubscriptionAllowedFor", "any")))
            .objectMapper(productionMapper)
            .validation(new DefaultValidationProvider())
            .build();
    executable.activate(ctx);
  }

  private WebhookProcessingPayload payloadWith(String rawBody, Map<String, String> headers) {
    WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
    when(payload.method()).thenReturn("POST");
    when(payload.headers()).thenReturn(headers);
    when(payload.params()).thenReturn(Map.of());
    when(payload.rawBody()).thenReturn(rawBody.getBytes(StandardCharsets.UTF_8));
    return payload;
  }
}
