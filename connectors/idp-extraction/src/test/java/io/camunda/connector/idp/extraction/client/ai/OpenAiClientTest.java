/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiClientTest {

  /**
   * Invokes the private sanitizeHeaderValues method via reflection for isolated testing of the
   * sanitization logic without constructing a full OpenAiChatModel.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, String> invokeSanitize(Map<String, String> headers) throws Exception {
    Method method = OpenAiClient.class.getDeclaredMethod("sanitizeHeaderValues", Map.class);
    method.setAccessible(true);
    return (Map<String, String>) method.invoke(null, headers);
  }

  @Test
  void shouldPassThroughValidHeaderValues() throws Exception {
    var headers = Map.of("Authorization", "Bearer sk-proj-abc123");

    var result = invokeSanitize(headers);

    assertThat(result).containsEntry("Authorization", "Bearer sk-proj-abc123");
  }

  @Test
  void shouldReplaceUnicodeSeparatorWithSpace() throws Exception {
    // U+2028 Line Separator between "Bearer" and "token"
    var headers = Map.of("Authorization", "Bearer\u2028token");

    var result = invokeSanitize(headers);

    assertThat(result).containsEntry("Authorization", "Bearer token");
  }

  @Test
  void shouldReplaceParagraphSeparator() throws Exception {
    // U+2029 Paragraph Separator
    var headers = Map.of("Authorization", "Bearer\u2029token");

    var result = invokeSanitize(headers);

    assertThat(result).containsEntry("Authorization", "Bearer token");
  }

  @Test
  void shouldStripLeadingAndTrailingWhitespace() throws Exception {
    var headers = Map.of("Authorization", "  Bearer token  ");

    var result = invokeSanitize(headers);

    assertThat(result).containsEntry("Authorization", "Bearer token");
  }

  @Test
  void shouldSkipNullValues() throws Exception {
    var headers = new LinkedHashMap<String, String>();
    headers.put("Authorization", "Bearer token");
    headers.put("X-Empty", null);

    var result = invokeSanitize(headers);

    assertThat(result).containsEntry("Authorization", "Bearer token").doesNotContainKey("X-Empty");
  }

  @Test
  void shouldSkipBlankValues() throws Exception {
    var headers = new LinkedHashMap<String, String>();
    headers.put("Authorization", "Bearer token");
    headers.put("X-Blank", "   ");

    var result = invokeSanitize(headers);

    assertThat(result).containsEntry("Authorization", "Bearer token").doesNotContainKey("X-Blank");
  }

  @Test
  void shouldReplaceControlCharacters() throws Exception {
    // Newline and carriage return in header value
    var headers = Map.of("Authorization", "Bearer\ntoken\r123");

    var result = invokeSanitize(headers);

    assertThat(result).containsEntry("Authorization", "Bearer token 123");
  }
}
