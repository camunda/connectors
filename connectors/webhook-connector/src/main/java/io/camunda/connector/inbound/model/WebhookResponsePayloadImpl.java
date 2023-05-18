package io.camunda.connector.inbound.model;

import io.camunda.connector.impl.inbound.WebhookResponsePayload;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class WebhookResponsePayloadImpl implements WebhookResponsePayload {
    
    public static final String DEFAULT_RESPONSE_STATUS_KEY = "webhookCallStatus";
    public static final String DEFAULT_RESPONSE_STATUS_VALUE_OK = "OK";
    public static final String DEFAULT_RESPONSE_STATUS_VALUE_FAIL = "FAIL";
    
    private Map<String, String> headers;
    private Object body;
    
    @Override
    public Map<String, String> headers() {
        return Optional.ofNullable(headers).orElse(Collections.emptyMap());
    }
    
    public void addHeader(String key, String value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put(key, value);
    }

    @Override
    public Object body() {
        return Optional.ofNullable(body).orElse(Map.of(DEFAULT_RESPONSE_STATUS_KEY, DEFAULT_RESPONSE_STATUS_VALUE_OK));
    }
    
    public void setBody(Object body) {
        this.body = body;
    }
}
