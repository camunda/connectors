/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.client.mapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * An HTTP response where the body is represented as a stream.
 *
 * <p>Lifecycle depends on the method that produced it:
 *
 * <ul>
 *   <li>From {@link io.camunda.connector.http.client.client.HttpClient#execute} (handler path) —
 *       the body is only valid INSIDE the {@link
 *       io.camunda.connector.http.client.mapper.ResponseMapper}; the underlying response is
 *       auto-closed by Apache HC after the mapper returns.
 *   <li>From {@link io.camunda.connector.http.client.client.HttpClient#executeStreaming}
 *       (open-response path) — the body owns the response, connection, and Apache client. The
 *       caller MUST close the body stream exactly once; that cascades through the resources.
 * </ul>
 */
public record StreamingHttpResponse(
    int status, String reason, Map<String, List<String>> headers, InputStream body) {}
