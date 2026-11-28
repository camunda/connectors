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
package io.camunda.connector.runtime.core.document;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.hostvalidator.CidrRange;
import io.camunda.connector.hostvalidator.HostDeniedException;
import io.camunda.connector.hostvalidator.HostIpValidator;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides which URLs an external document reference may be downloaded from.
 *
 * <p>An external document reference ({@code "camunda.document.type": "external"}) can come from
 * untrusted process data, such as an inbound webhook body, so its URL is checked right before the
 * download: only {@code http}/{@code https} URLs whose host resolves exclusively to public
 * addresses are allowed. Loopback, link-local (including cloud metadata endpoints), private,
 * reserved and special-use ranges are denied. Classification is delegated to {@link
 * HostIpValidator}, which checks every resolved address.
 *
 * @param enabled whether URLs are checked at all; self-managed deployments that download documents
 *     from internal servers, or whose runtime cannot resolve external hostnames itself (e.g. behind
 *     a forward proxy), can turn the check off
 * @param allowLoopback allow hosts resolving to loopback addresses; never exposed as a runtime
 *     setting, intended for tests that serve documents from a local server
 */
public record ExternalDocumentHostPolicy(boolean enabled, boolean allowLoopback) {

  private static final Logger LOG = LoggerFactory.getLogger(ExternalDocumentHostPolicy.class);

  public static final String ENABLED_PROPERTY =
      "camunda.connector.document.external.host-validation.enabled";

  /**
   * Ranges denied on top of {@link HostIpValidator}'s defaults: IPv6 site-local ({@code fec0::/10},
   * deprecated by RFC 3879 but still routed internally on some networks).
   */
  private static final List<CidrRange> ADDITIONAL_DENY_RANGES =
      List.of(CidrRange.parse("fec0::/10"));

  /** Checks every URL and denies every non-public address. */
  public static ExternalDocumentHostPolicy defaults() {
    return new ExternalDocumentHostPolicy(true, false);
  }

  /**
   * Throws {@link ConnectorInputException} unless {@code url} may be downloaded under this policy.
   */
  public void validate(String url) {
    if (!enabled) {
      return;
    }
    String host = extractHost(url);
    try {
      HostIpValidator.validate(host, List.of(), ADDITIONAL_DENY_RANGES, false, allowLoopback);
    } catch (HostDeniedException | UnknownHostException e) {
      // The same message for both cases, without the resolved address, so that error output
      // cannot be used to probe which internal hostnames exist or what they resolve to.
      LOG.debug("Denied external document download from host '{}'", host, e);
      throw denied(
          "the host could not be resolved or resolves to a non-public address. To download"
              + " documents from internal hosts on a self-managed runtime, set '"
              + ENABLED_PROPERTY
              + "=false'",
          null);
    }
  }

  private static String extractHost(String url) {
    if (url == null || url.isBlank()) {
      throw denied("the URL is empty", null);
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw denied("the URL is not valid", e);
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw denied("only http and https URLs are supported", null);
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw denied("the URL has no host", null);
    }
    return host;
  }

  private static ConnectorInputException denied(String reason, Throwable cause) {
    return new ConnectorInputException(
        "The external document URL is not allowed: " + reason + ".", cause);
  }
}
