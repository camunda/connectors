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
package io.camunda.connector.http.client.model;

import java.io.IOException;
import java.io.InputStream;

public class ResponseBody implements AutoCloseable {

  private final InputStream bodyStream;
  private byte[] bytesCache = null;
  private boolean isConsumed = false;

  public ResponseBody(InputStream bodyStream) {
    this.bodyStream = bodyStream;
  }

  public InputStream getStream() {
    return bodyStream;
  }

  /**
   * Reads all bytes from the body stream. If the stream has already been consumed, it returns the
   * cached bytes.
   *
   * <p>Note that this method consumes the stream and caches the result in memory for future calls.
   * Use with caution for large streams.
   *
   * @return the bytes read from the stream, or null if the stream is null
   * @throws IOException if an I/O error occurs when reading the stream
   */
  public byte[] readBytes() throws IOException {
    if (isConsumed) {
      return bytesCache;
    }
    if (bodyStream == null) {
      return null;
    }
    bytesCache = bodyStream.readAllBytes();
    isConsumed = true;
    return bytesCache;
  }

  @Override
  public void close() throws IOException {
    if (bodyStream != null) {
      bodyStream.close();
    }
  }
}
