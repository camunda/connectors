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
package io.camunda.connector.hostvalidator;

import java.net.InetAddress;

/** Thrown when a hostname resolves to an IP address that is disallowed. */
public class HostDeniedException extends RuntimeException {

  private final String host;
  private final InetAddress resolvedAddress;
  private final HostIpValidator.Classification classification;

  public HostDeniedException(
      String host, InetAddress resolvedAddress, HostIpValidator.Classification classification) {
    super(
        "Host '"
            + host
            + "' resolved to '"
            + (resolvedAddress != null ? resolvedAddress.getHostAddress() : "<none>")
            + "' was denied: "
            + classification);
    this.host = host;
    this.resolvedAddress = resolvedAddress;
    this.classification = classification;
  }

  public String host() {
    return host;
  }

  public InetAddress resolvedAddress() {
    return resolvedAddress;
  }

  public HostIpValidator.Classification classification() {
    return classification;
  }
}
