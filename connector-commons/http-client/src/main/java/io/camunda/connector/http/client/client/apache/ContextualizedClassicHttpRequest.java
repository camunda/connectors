package io.camunda.connector.http.client.client.apache;

import org.apache.hc.core5.http.ClassicHttpRequest;

public class ContextualizedClassicHttpRequest {

    ClassicHttpRequest request;
    boolean wasTokenCached;
    Runnable revokeTokenCallback;

}
