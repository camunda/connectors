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
package io.camunda.connector.http.base.client.apache.builder.parts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UrlEncoderTest {
  private static Stream<Arguments> urlEncodingTestCases() {
    return Stream.of(
        // RFC3986: https://datatracker.ietf.org/doc/html/rfc3986
        // pchar  = unreserved / pct-encoded / sub-delims (e.g. , and !) / ":" / "@"

        // Path
        Arguments.of(
            "http://localhost:8080/hello, world!",
            "http://localhost:8080/hello%2C%20world%21"), // not-encoded url with invalid char
        Arguments.of(
            "http://localhost:8080/hello, %20world!",
            "http://localhost:8080/hello%2C%20%2520world%21"), // partially-encoded url with invalid
        // char
        Arguments.of(
            "http://localhost:8080/hello%2C%20world%21",
            "http://localhost:8080/hello%2C%20world%21"), // fully-encoded url with invalid char
        Arguments.of(
            "http://localhost:8080/hello,world!",
            "http://localhost:8080/hello%2Cworld%21"), // not-encoded url with valid chars
        Arguments.of(
            "http://localhost:8080/hello%2Cworld!",
            "http://localhost:8080/hello%252Cworld%21"), // partially-encoded url with valid chars
        Arguments.of(
            "http://localhost:8080/hello%2Cworld%21",
            "http://localhost:8080/hello%2Cworld%21"), // fully-encoded url with invalid char
        Arguments.of(
            "http://localhost:8080/über uns",
            "http://localhost:8080/%C3%BCber%20uns"), // not-encoded with UTF-8
        Arguments.of(
            "http://localhost:8080/überuns",
            "http://localhost:8080/%C3%BCberuns"), // not-encoded with UTF-8
        Arguments.of(
            "http://localhost:8080/hello,&world!",
            "http://localhost:8080/hello%2C%26world%21"), // not-encoded url with reserved char
        Arguments.of(
            "http://localhost:8080/hello,%26world!",
            "http://localhost:8080/hello%2C%2526world%21"), // partially-encoded url with reserved
        // char
        Arguments.of(
            "http://localhost:8080/hello%2C%26world%21",
            "http://localhost:8080/hello%2C%26world%21"), // fully-encoded url with reserved char

        // Query = *( pchar / "/" / "?" )
        Arguments.of(
            "http://localhost:8080/test?query=hello:world?",
            "http://localhost:8080/test?query=hello%3Aworld%3F"), // not-encoded url with invalid
        // char
        Arguments.of(
            "http://localhost:8080/test?query=hello%3Fworld?",
            "http://localhost:8080/test?query=hello%253Fworld%3F"), // partially-encoded url with
        // invalid char
        Arguments.of(
            "http://localhost:8080/test?query=hello%3Aworld%3F",
            "http://localhost:8080/test?query=hello%3Aworld%3F"), // fully-encoded url with invalid
        // char
        Arguments.of(
            "http://localhost:8080/test?query=hello world?",
            "http://localhost:8080/test?query=hello%20world%3F"), // not-encoded url with invalid
        // char
        Arguments.of(
            "http://localhost:8080/test?param1=value1&param2=value 2",
            "http://localhost:8080/test?param1=value1&param2=value%202"), // not-encoded url with
        // invalid char
        Arguments.of(
            "http://localhost:8080/test?query=hello world%3F",
            "http://localhost:8080/test?query=hello%20world%253F"), // partially-encoded url with
        // invalid char
        Arguments.of(
            "http://localhost:8080/test?mark?value%3F",
            "http://localhost:8080/test?mark%3Fvalue%253F"), // partially-encoded url with invalid
        // char
        Arguments.of(
            "http://localhost:8080/test?query=hello%20world%3F",
            "http://localhost:8080/test?query=hello%20world%3F"), // fully-encoded url with invalid
        // char
        Arguments.of(
            "http://localhost:8080/test?query=hello,world!",
            "http://localhost:8080/test?query=hello,world!"), // not-encoded/fully-encoded url with
        // valid chars
        Arguments.of(
            "http://localhost:8080/test?query=hello%20world!",
            "http://localhost:8080/test?query=hello%20world!"), // partially-encoded url with valid
        // chars

        // Plus sign (in path must be encoded, in query is valid)
        Arguments.of(
            "http://localhost:8080/hello + world", "http://localhost:8080/hello%20%2B%20world"),
        Arguments.of(
            "http://localhost:8080/test?hello+world", "http://localhost:8080/test?hello+world"),

        // Fragment
        Arguments.of(
            "http://localhost:8080/foo/bar#section 2", "http://localhost:8080/foo/bar#section%202"),
        Arguments.of(
            "http://localhost:8080/foo/bar#section:2", "http://localhost:8080/foo/bar#section%3A2"),
        Arguments.of(
            "http://localhost:8080/foo/bar#section:%202",
            "http://localhost:8080/foo/bar#section%3A%25202"),
        Arguments.of(
            "http://localhost:8080/foo/bar#section%202",
            "http://localhost:8080/foo/bar#section%202"),

        // user info, port, empty path
        Arguments.of(
            "http://user:pass@localhost:8080/secret", "http://user:pass@localhost:8080/secret"),
        Arguments.of("http://localhost:1234/port/test", "http://localhost:1234/port/test"),
        Arguments.of("http://localhost:8080", "http://localhost:8080"));
  }

  @ParameterizedTest
  @MethodSource("urlEncodingTestCases")
  public void shouldHandleEncoding(String requestUrl, String expectedEncodedUrl) {
    Boolean skipEncoding = false;
    var result = UrlEncoder.toEncodedUri(requestUrl, skipEncoding);
    assertThat(result.toString()).isEqualTo(expectedEncodedUrl);
  }

  private static Stream<Arguments> urlEncodingInvalidTestCases() {
    return Stream.of(
        Arguments.of("localhost:8080/test"), Arguments.of("http://localhost:abc/test"));
  }

  @ParameterizedTest
  @MethodSource("urlEncodingInvalidTestCases")
  public void shouldThrowException(String requestUrl) {
    Boolean skipEncoding = false;
    assertThrows(
        ConnectorInputException.class, () -> UrlEncoder.toEncodedUri(requestUrl, skipEncoding));
  }

  private static Stream<Arguments> urlEncodingValidTestCasesWithSkipEncoding() {
    return Stream.of(
        Arguments.of("http://localhost:8080/hello,world!"),
        Arguments.of("http://localhost:8080/hello%2Cworld%21"),
        Arguments.of("http://localhost:8080/hello,%20world!"),
        Arguments.of("http://localhost:8080/test?hello+world"));
  }

  @ParameterizedTest
  @MethodSource("urlEncodingValidTestCasesWithSkipEncoding")
  public void shouldHandleSkipEncoding(String requestUrl) {
    Boolean skipEncoding = true;
    var result = UrlEncoder.toEncodedUri(requestUrl, skipEncoding);
    assertThat(result.toString()).isEqualTo(requestUrl);
  }

  private static Stream<Arguments> urlEncodingInvalidTestCasesWithSkipEncoding() {
    return Stream.of(
        Arguments.of("http://localhost:8080/hello world"),
        Arguments.of("http://localhost:8080/hello%20world?invalid query"),
        Arguments.of("http://localhost:8080/hello%20world#invalid fragment"));
  }

  @ParameterizedTest
  @MethodSource("urlEncodingInvalidTestCasesWithSkipEncoding")
  public void shouldThrowException_whenSkipEncodingWithInvalidURL(String requestUrl) {
    Boolean skipEncoding = true;
    assertThrows(
        ConnectorInputException.class, () -> UrlEncoder.toEncodedUri(requestUrl, skipEncoding));
  }
}
