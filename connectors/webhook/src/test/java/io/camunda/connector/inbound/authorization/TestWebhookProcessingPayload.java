package io.camunda.connector.inbound.authorization;

import com.google.common.net.HttpHeaders;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;

import java.util.HashMap;
import java.util.Map;

public class TestWebhookProcessingPayload implements WebhookProcessingPayload {

    protected String jwtToken;
    protected String reqBody;

    TestWebhookProcessingPayload(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    TestWebhookProcessingPayload(String jwtToken, String reqBody) {
        this.jwtToken = jwtToken;
        this.reqBody = reqBody;
    }

    @Override
    public String method() {
        // omitted intentionally
        return null;
    }

    @Override
    public Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.put("Authorization", "Bearer " + this.jwtToken);
        return headers;
    }

    @Override
    public Map<String, String> params() {
        // omitted intentionally
        return null;
    }

    @Override
    public byte[] rawBody() {
        byte[] byteArray = reqBody == null ? new byte[0] : this.reqBody.getBytes();
        return byteArray;
    }
}
