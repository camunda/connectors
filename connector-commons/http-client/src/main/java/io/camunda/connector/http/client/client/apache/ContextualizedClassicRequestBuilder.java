package io.camunda.connector.http.client.client.apache;

import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public class ContextualizedClassicRequestBuilder {

    ClassicRequestBuilder request;
    boolean wasTokenCached;
    Runnable revokeTokenCallback;

}
