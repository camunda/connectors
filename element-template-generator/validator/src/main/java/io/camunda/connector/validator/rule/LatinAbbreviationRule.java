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
package io.camunda.connector.validator.rule;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LatinAbbreviationRule extends HelpTextRule {

  private static final Pattern LATIN = Pattern.compile("(?i)\\b(e\\.\\s?g\\.|i\\.\\s?e\\.|etc\\.)");

  @Override
  protected Optional<String> check(String text) {
    Matcher matcher = LATIN.matcher(text);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(
        "Replace \""
            + matcher.group(1)
            + "\" with plain English (\"for example\", \"that is\", \"and so on\").");
  }
}
