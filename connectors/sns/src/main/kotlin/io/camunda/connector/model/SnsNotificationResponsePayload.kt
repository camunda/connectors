package io.camunda.connector.model

import io.camunda.connector.api.inbound.InboundConnectorResult
import io.camunda.connector.impl.inbound.WebhookResponsePayload

data class SnsNotificationResponsePayload(
    val headers: Map<String, String>?,
    val body: Any?,
    val result: InboundConnectorResult<*>?) : WebhookResponsePayload {
    override fun headers(): MutableMap<String, String>? {
        return null
    }

    override fun body(): Any? {
        return "OK"
    }

    override fun executionResult(): InboundConnectorResult<*>? {
        return null
    }
}
