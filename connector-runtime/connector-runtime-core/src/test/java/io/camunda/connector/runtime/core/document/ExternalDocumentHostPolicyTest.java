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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExternalDocumentHostPolicyTest {

  private final ExternalDocumentHostPolicy defaults = ExternalDocumentHostPolicy.defaults();

  @ParameterizedTest
  @ValueSource(
      strings = {
        // the host is what follows the userinfo, as for the HTTP client
        "http://8.8.8.8@127.0.0.1:8080/actuator/health",
        "http://8.8.8.8:80@169.254.169.254/",
        // a backslash is not valid in a URI, so it cannot shift the host
        "http://8.8.8.8\\@127.0.0.1/",
        // names and short forms that end up on loopback
        "http://localhost./",
        "http://127.1/"
      })
  void deniesUrlTricksThatEndAtAnInternalHost(String url) {
    assertThatThrownBy(() -> defaults.validate(url)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void anInternalAddressInTheUserinfoDoesNotDecideTheHost() {
    // the request goes to 8.8.8.8, so it is allowed: the userinfo is not the target
    assertThatCode(() -> defaults.validate("http://127.0.0.1:8080@8.8.8.8/actuator/health"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // cloud metadata endpoints (link-local)
        "http://169.254.169.254/latest/meta-data/",
        "http://[fd00:ec2::254]/latest/meta-data/",
        // the same metadata address spelled differently
        "http://2852039166/",
        "http://[::ffff:a9fe:a9fe]/",
        // loopback, including hostnames resolving to it
        "http://127.0.0.1:8080/actuator/env",
        "http://[::1]/",
        "http://localhost:8080/",
        "http://0.0.0.0:8080/",
        // private ranges
        "http://10.0.0.1/",
        "http://172.16.0.1/",
        "http://192.168.1.1/",
        // IPv6 site-local, not covered by the shared classifier
        "http://[fec0::1]/secret",
        // CGNAT, which the standard library does not treat as private
        "http://100.64.0.1/",
        // scheme is matched case-insensitively, so this is still checked
        "HTTP://169.254.169.254/"
      })
  void deniesNonPublicHosts(String url) {
    assertThatThrownBy(() -> defaults.validate(url))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("The external document URL is not allowed");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file:///etc/passwd",
        "jar:file:///tmp/a.jar!/secret",
        "ftp://example.com/file",
        "gopher://127.0.0.1:6379/_",
        "http:///no-host",
        "http://exa mple.com/",
        "/relative/path",
        "",
        "   "
      })
  void deniesUrlsThatAreNotHttpWithAHost(String url) {
    assertThatThrownBy(() -> defaults.validate(url))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("The external document URL is not allowed");
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://8.8.8.8/file.pdf", "https://1.1.1.1/file.pdf"})
  void allowsPublicHosts(String url) {
    assertThatCode(() -> defaults.validate(url)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://169.254.169.254/",
        "http://10.0.0.1/",
        "http://[fd12::1]/",
        "http://127.0.0.1/",
        "http://host.that.does.not.resolve.invalid/"
      })
  void disabledPolicyAllowsAnyHost(String url) {
    assertThatThrownBy(() -> defaults.validate(url)).isInstanceOf(ConnectorInputException.class);
    assertThatCode(() -> new ExternalDocumentHostPolicy(false, false).validate(url))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://localhost/", "http://unresolvable.invalid/"})
  void unresolvableAndNonPublicHostsGetTheSameMessage(String url) {
    assertThatThrownBy(() -> defaults.validate(url))
        .hasMessage(
            "The external document URL is not allowed: the host could not be resolved or resolves"
                + " to a non-public address. To download documents from internal hosts on a"
                + " self-managed runtime, set '"
                + ExternalDocumentHostPolicy.ENABLED_PROPERTY
                + "=false'.")
        .hasNoCause();
  }
}
