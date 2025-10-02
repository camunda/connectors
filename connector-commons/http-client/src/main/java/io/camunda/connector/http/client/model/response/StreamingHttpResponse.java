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
package io.camunda.connector.http.client.model.response;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** An HTTP response where the body is represented as a stream. */
public class StreamingHttpResponse implements HttpResponse<InputStream>, AutoCloseable {

  private final Runnable onClose;
  private final int status;
  private final String reason;
  private final Map<String, List<String>> headers;
  private final InputStream body;

  public StreamingHttpResponse(int status, String reason, Map<String, List<String>> headers, InputStream body, Runnable onClose) {
    this.status = status;
    this.reason = reason;
    this.headers = headers;
    this.body = body;
    this.onClose = onClose;
  }

  @Override
  public int status() {
    return status;
  }

  @Override
  public String reason() {
    return reason;
  }

  @Override
  public InputStream body() {
    return body;
  }

  @Override
  public Map<String, List<String>> headers() {
    return headers;
  }

  @Override
  public void close() {
    onClose.run();
    if (body != null) {
      try {
        body.close();
      } catch (IOException e) {
        throw new RuntimeException("Failed to close response body stream", e);
      }
    }
  }
}
