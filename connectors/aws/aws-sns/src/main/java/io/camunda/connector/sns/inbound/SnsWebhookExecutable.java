/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound;

// TODO: These v1 imports are required because AWS SDK v2 has no equivalent of SnsMessageManager
//  for parsing and verifying SNS HTTP webhook messages (signature verification).
//  Migrate once resolved: https://github.com/aws/aws-sdk-java-v2/issues/1302
import com.amazonaws.services.sns.message.SnsMessage;
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;
import com.amazonaws.services.sns.message.SnsSubscriptionConfirmation;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.sns.inbound.model.SnsWebhookConnectorProperties;
import io.camunda.connector.sns.inbound.model.SnsWebhookConnectorProperties.SnsWebhookConnectorPropertiesWrapper;
import io.camunda.connector.sns.inbound.model.SnsWebhookProcessingResult;
import io.camunda.connector.sns.inbound.model.SubscriptionAllowListFlag;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "AWS SNS Inbound", type = "io.camunda:aws-sns-webhook:1")
@ElementTemplate(
    engineVersion = "^8.9",
    id = "io.camunda.connectors.AWSSNS.inbound.v1",
    name = "SNS HTTPS Connector",
    icon = "icon.svg",
    version = 7,
    inputDataClass = SnsWebhookConnectorPropertiesWrapper.class,
    description = "Receive messages from AWS SNS via HTTPS.",
    keywords = {
      "receive event",
      "receive message",
      "notification",
      "subscribe to topic",
      "event driven"
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sns/?amazonsns=inbound",
    propertyGroups = {@PropertyGroup(id = "subscription", label = "Subscription Configuration")},
    elementTypes = {
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.AWSSNS.MessageStartEvent.v1",
          templateNameOverride = "SNS HTTPS Message Start Event Connector Subscription"),
      @ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.AWSSNS.IntermediateCatchEvent.v1",
          templateNameOverride = "SNS HTTPS Intermediate Catch Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.AWSSNS.Boundary.v1",
          templateNameOverride = "SNS HTTPS Boundary Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.RECEIVE_TASK,
          elementType = BpmnType.RECEIVE_TASK,
          templateIdOverride = "io.camunda.connectors.inbound.AWSSNS.Receive.v1",
          templateNameOverride = "SNS HTTPS Receive Task Connector")
    })
public class SnsWebhookExecutable implements WebhookConnectorExecutable {
  private static final Logger LOG = LoggerFactory.getLogger(SnsWebhookExecutable.class);
  protected static final String TOPIC_ARN_HEADER = "x-amz-sns-topic-arn";

  private final ObjectMapper objectMapper;
  private final SnsClientSupplier snsClientSupplier;

  private InboundConnectorContext context;
  private SnsWebhookConnectorProperties props;

  public SnsWebhookExecutable() {
    this(ObjectMapperSupplier.getMapperInstance(), new SnsClientSupplier());
  }

  public SnsWebhookExecutable(
      final ObjectMapper objectMapper, final SnsClientSupplier snsClientSupplier) {
    this.objectMapper = objectMapper;
    this.snsClientSupplier = snsClientSupplier;
  }

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload webhookProcessingPayload)
      throws Exception {

    // The webhook endpoint stays registered even when activate() failed validation (e.g. a
    // legacy/hand-authored element with an invalid or missing allow-list configuration), so a
    // request can still reach this method with props unset. Fail with the same actionable
    // rejection as everything else here, not an NPE surfaced as an opaque HTTP 500.
    if (props == null) {
      throw new Exception("Connector is not activated (invalid configuration); rejecting request.");
    }
    // Reject obvious misses before the expensive signature verification. The second allow-list
    // check below remains authoritative because the header is not covered by the SNS signature.
    checkMessageAllowListed(webhookProcessingPayload.headers().get(TOPIC_ARN_HEADER));
    String region = extractRegionFromTopicArnHeader(webhookProcessingPayload.headers());
    SnsMessageManager msgManager = snsClientSupplier.messageManager(region);
    SnsMessage msg =
        msgManager.parseMessage(new ByteArrayInputStream(webhookProcessingPayload.rawBody()));
    String verifiedTopicArn = msg.getTopicArn();
    checkMessageAllowListed(verifiedTopicArn);
    Map bodyAsMap = objectMapper.readValue(webhookProcessingPayload.rawBody(), Map.class);
    if (msg instanceof SnsSubscriptionConfirmation ssc) {
      return tryConfirmSubscription(webhookProcessingPayload, bodyAsMap, ssc);
    } else if (msg instanceof SnsNotification) {
      return handleNotification(webhookProcessingPayload, bodyAsMap);
    } else {
      String errorMessage = "Operation not supported: " + msg.getClass().getName();
      throw new IOException(errorMessage);
    }
  }

  private SnsWebhookProcessingResult tryConfirmSubscription(
      WebhookProcessingPayload webhookProcessingPayload,
      Map bodyAsMap,
      SnsSubscriptionConfirmation confirmation) {
    // If request was tampered, or insufficient ACL, confirmation will throw an exception
    confirmation.confirmSubscription();

    return new SnsWebhookProcessingResult(
        new MappedHttpRequest(
            bodyAsMap, webhookProcessingPayload.headers(), webhookProcessingPayload.params()),
        Map.of("snsEventType", "Subscription"));
  }

  private SnsWebhookProcessingResult handleNotification(
      WebhookProcessingPayload webhookProcessingPayload, Map bodyAsMap) {
    return new SnsWebhookProcessingResult(
        new MappedHttpRequest(
            bodyAsMap, webhookProcessingPayload.headers(), webhookProcessingPayload.params()),
        Map.of("snsEventType", "Notification"));
  }

  // A null securitySubscriptionAllowedFor (hand-authored BPMN, or a diagram built on an older
  // template) is treated the same as "specific": only an explicit "any" skips the allow list.
  private void checkMessageAllowListed(String topicArn) throws Exception {
    if (!SubscriptionAllowListFlag.any.equals(props.securitySubscriptionAllowedFor())
        && !isAllowListed(props.topicsAllowList(), topicArn)) {
      // The first call site passes the caller-controlled header, before signature verification,
      // so this value is not yet trustworthy: strip CR/LF before it reaches any log line (log
      // injection, CWE-117). context is only @NotBlank-validated (no CR/LF restriction) and is
      // sanitized here too, for the same reason.
      String sanitizedTopicArn = sanitizeForLog(topicArn);
      String sanitizedContext = sanitizeForLog(props.context());
      // Deliberately omits the allow-list contents and any request payload in both the log and
      // the exception message below (InboundWebhookRestController logs the exception message
      // into the connector's activity log): operators get enough to see the attempt (subscription
      // id, rejected topic) without this becoming a config or data leak.
      LOG.error(
          "Rejected SNS message for subscription '{}': topic '{}' is not allow-listed",
          sanitizedContext,
          sanitizedTopicArn);
      throw new Exception(
          "Request didn't match allow list. Request coming from " + sanitizedTopicArn);
    }
  }

  private static String sanitizeForLog(String value) {
    return value == null ? null : value.replaceAll("[\r\n]", "_");
  }

  // The comma-separated string path is trimmed per-entry by FeelDeserializer.handleListLikeFormat
  // before it ever reaches here, but a FEEL list literal (e.g. =[" arnA ", "arnB"]) is not - so a
  // padded entry needs trimming at comparison time too, or it silently rejects an allow-listed
  // topic (the same over-block failure mode the comma path was already fixed for).
  private static boolean isAllowListed(List<String> allowList, String topicArn) {
    return allowList.stream().anyMatch(entry -> entry != null && entry.trim().equals(topicArn));
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    if (context == null) {
      throw new Exception("Inbound connector context cannot be null");
    }
    this.context = context;
    props = context.bindProperties(SnsWebhookConnectorPropertiesWrapper.class).inbound();
    context.reportHealth(Health.up());
  }

  // Topic ARN header has a format arn:aws:sns:region-xyz:000011112222:TopicName, and
  // we need to extract region from it, which is at index 3, given string is separated by ':'
  private String extractRegionFromTopicArnHeader(final Map<String, String> headers)
      throws Exception {
    final var topicArn =
        Optional.ofNullable(headers.get(TOPIC_ARN_HEADER))
            .orElseThrow(
                () -> new Exception("SNS request did not contain header: " + TOPIC_ARN_HEADER));
    final var topicArnParts = topicArn.split(":", 6);
    if (topicArnParts.length != 6
        || !"arn".equals(topicArnParts[0])
        || topicArnParts[1].isBlank()
        || !"sns".equals(topicArnParts[2])
        || topicArnParts[3].isBlank()
        || topicArnParts[4].isBlank()
        || topicArnParts[5].isBlank()) {
      // This runs unconditionally, in every mode including "any", before checkMessageAllowListed
      // has a chance to reject anything - sanitize here too (log injection, CWE-117).
      throw new Exception("Invalid SNS topic ARN header: " + sanitizeForLog(topicArn));
    }
    return topicArnParts[3];
  }

  @Override
  public void deactivate() throws Exception {
    context.reportHealth(Health.down());
  }
}
