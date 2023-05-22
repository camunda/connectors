package io.camunda.connector

import com.amazonaws.services.sns.message.SnsMessageManager
import com.amazonaws.services.sns.message.SnsNotification
import com.amazonaws.services.sns.message.SnsSubscriptionConfirmation
import io.camunda.connector.api.annotation.InboundConnector
import io.camunda.connector.api.inbound.InboundConnectorContext
import io.camunda.connector.api.inbound.InboundConnectorResult
import io.camunda.connector.api.inbound.WebhookConnectorExecutable
import io.camunda.connector.impl.inbound.WebhookRequestPayload
import io.camunda.connector.impl.inbound.WebhookResponsePayload
import io.camunda.connector.model.SnsNotificationResponsePayload
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

@InboundConnector(name = "SNS_NOTIFICATION", type = "io.camunda:connector-sns-inbound:1")
class SnsNotificationExecutable: WebhookConnectorExecutable {

    private val log = LoggerFactory.getLogger(this.javaClass)
    
    override fun triggerWebhook(ctx: InboundConnectorContext, payload: WebhookRequestPayload): WebhookResponsePayload {
        log.error("IGPETROV: triggered SNS_NOTIFICATION")
        log.error("Context: $ctx, payload: $payload")
        ctx.replaceSecrets(ctx.properties) // Is it really properties??
        val region = "eu-central-1"
        val snsMessageManager = SnsMessageManager(region)
        val message = snsMessageManager.parseMessage(ByteArrayInputStream(payload.rawBody()))
        return when (message) {
            is SnsSubscriptionConfirmation -> confirmSubscription(message)
            is SnsNotification -> handleNotification(message, ctx)
            else -> allOtherConditions()
        }
    }
    
    private fun confirmSubscription(subscription: SnsSubscriptionConfirmation): SnsNotificationResponsePayload {
        // TODO: handle result properly
        subscription.confirmSubscription()
        return SnsNotificationResponsePayload(headers = null, result = null, body = mapOf("SNSConnector" to "SUBSCRIBED"))
    }
    
    private fun handleNotification(notification: SnsNotification, ctx: InboundConnectorContext): 
            SnsNotificationResponsePayload {
        val result: InboundConnectorResult<*> = ctx.correlate(
            mapOf("MessageId" to notification.messageId,
                "Subject" to notification.subject,
                "Message" to notification.message,
                "Timestamp" to notification.timestamp)) // TODO: add message attributes
        return SnsNotificationResponsePayload(headers = null, result = result, body = mapOf("SNSConnector" to "NOTIFICATION_TRIGGERED")) 
    }
    
    private fun allOtherConditions(): SnsNotificationResponsePayload {
        log.error("Stub for now")
        return SnsNotificationResponsePayload(headers = null, result = null, body = mapOf("SNSConnector" to "UNKNOWN"))
    }
}