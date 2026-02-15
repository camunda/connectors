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
package io.camunda.connector.http.client.client.jdk.proxy;

/**
 * Normalizes protocol/scheme strings from the JDK, which may include version suffixes like {@code
 * "http/1.1"} or {@code "HTTP/1.1"}, to their lowercase base form (e.g. {@code "http"}).
 */
final class ProtocolNormalizer {

  private ProtocolNormalizer() {}

  /**
   * Returns the lowercased part before the first {@code /}, or the full value lowercased if no
   * slash is present. Returns {@code null} if the input is {@code null}.
   */
  static String normalize(String protocol) {
    if (protocol == null) {
      return null;
    }
    int slash = protocol.indexOf('/');
    return (slash > 0 ? protocol.substring(0, slash) : protocol).toLowerCase();
  }
}
