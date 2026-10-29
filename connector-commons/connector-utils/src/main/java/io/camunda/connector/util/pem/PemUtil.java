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
package io.camunda.connector.util.pem;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs PEM text (certificates, private keys, ...) whose line breaks were lost on the way in.
 *
 * <p>Single-line inputs, such as the ones Hub renders for reusable credentials, strip the line
 * breaks of a pasted PEM, and environment variables often carry them as a literal {@code \n}. Java
 * and BouncyCastle PEM parsers reject both. Connectors that accept a PEM from a text property
 * should pass it through {@link #normalize(String)} before parsing it.
 */
public final class PemUtil {

  private static final int LINE_LENGTH = 64;

  private static final Pattern PEM_BLOCK =
      Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);

  private PemUtil() {}

  /**
   * Rebuilds every PEM block in the input as a header line, the base64 body wrapped at 64
   * characters, and a footer line. Blocks are separated by a single line break, so a certificate
   * chain survives. The result is idempotent: well-formed PEM comes back unchanged apart from
   * surrounding whitespace.
   *
   * <p>Literal {@code \n} and {@code \r\n} sequences are treated as line breaks. A block with RFC
   * 1421 headers (for example {@code Proc-Type}) is left as it is, because its header lines carry
   * meaning. Input without a PEM block is returned trimmed, so the parser can report the real
   * problem.
   *
   * @param pem the PEM text, possibly flattened onto one line
   * @return the PEM text with line breaks restored, or the input when it is {@code null}
   */
  public static String normalize(String pem) {
    if (pem == null) {
      return null;
    }
    String text = pem.replace("\\r\\n", "\n").replace("\\n", "\n").trim();

    Matcher matcher = PEM_BLOCK.matcher(text);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      if (!result.isEmpty()) {
        result.append('\n');
      }
      result.append(rebuildBlock(matcher.group(1), matcher.group(2), matcher.group()));
    }
    return result.isEmpty() ? text : result.toString();
  }

  private static String rebuildBlock(String label, String body, String original) {
    if (body.contains(":")) {
      return original;
    }
    StringBuilder block = new StringBuilder("-----BEGIN ").append(label).append("-----\n");

    String base64 = body.replaceAll("\\s+", "");
    for (int i = 0; i < base64.length(); i += LINE_LENGTH) {
      block.append(base64, i, Math.min(base64.length(), i + LINE_LENGTH)).append('\n');
    }

    return block.append("-----END ").append(label).append("-----").toString();
  }
}
