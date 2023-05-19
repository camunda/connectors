package io.camunda.connector.runtime.inbound.webhook.model;

import io.camunda.connector.impl.inbound.WebhookRequestPayload;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class HttpServletRequestWebhookRequestPayload implements WebhookRequestPayload {
    
    private final String method;
    private final Map<String, String> headers;
    private final Map<String, String> params;
    private final byte[] rawBody;
    

    public HttpServletRequestWebhookRequestPayload(
            final HttpServletRequest httpServletRequest,
            final Map<String, String> params,
            final Map<String, String> headers,
            byte[] bodyAsByteArray) {
        this.method = httpServletRequest.getMethod();
        this.headers = headers;
        this.params = params;
        this.rawBody = bodyAsByteArray;
    }
    
    @Override
    public String method() {
        return method;
    }

    @Override
    public Map<String, Object> headers() {
        return Collections.unmodifiableMap(Optional.ofNullable(headers).orElse(Collections.emptyMap()));
    }

    @Override
    public Map<String, Object> params() {
        return Collections.unmodifiableMap(Optional.ofNullable(params).orElse(Collections.emptyMap()));
    }

    @Override
    public byte[] rawBody() {
        return rawBody != null ? Arrays.copyOf(rawBody, rawBody.length) : null;
    }


    @Override
    public String toString() {
        return "SpringRequestWebhookRequestPayload{" +
                "method='" + method + '\'' +
                ", headers=" + headers +
                ", params=" + params +
                ", rawBody=" + Arrays.toString(rawBody) +
                '}';
    }
}
