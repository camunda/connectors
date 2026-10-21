/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.connection;

import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport.OkHttpProxy;
import java.time.Duration;
import java.util.Optional;

/**
 * How a chat model provider connects to its endpoint, resolved once for all providers. A provider
 * factory only adapts these values to the client builder of its SDK.
 *
 * @param timeout timeout of a model call, which also bounds the reading and writing of the response
 * @param connectTimeout timeout of the TCP connection, never longer than {@code timeout}
 * @param proxy the proxy to connect through, empty if the target is not proxied or its host is a
 *     non-proxy host
 */
public record ConnectionSettings(
    Duration timeout, Duration connectTimeout, Optional<OkHttpProxy> proxy) {}
