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
package io.camunda.connector.e2e.agenticai.assertj;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * Compares {@link ToolExecutionResultMessage} instances using JSON-equivalent comparison for the
 * text field, falling back to plain string equality for non-JSON content.
 */
public class ToolExecutionResultMessageEqualsPredicate
    implements BiPredicate<ToolExecutionResultMessage, ToolExecutionResultMessage> {

  @Override
  public boolean test(ToolExecutionResultMessage a, ToolExecutionResultMessage b) {
    if (!Objects.equals(a.id(), b.id())) {
      return false;
    }

    if (!Objects.equals(a.toolName(), b.toolName())) {
      return false;
    }

    return jsonEquals(a.text(), b.text());
  }

  private static boolean jsonEquals(String a, String b) {
    if (Objects.equals(a, b)) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    try {
      return !JSONCompare.compareJSON(a, b, JSONCompareMode.STRICT).failed();
    } catch (JSONException e) {
      // not valid JSON (and not equal strings, since Objects.equals check above handles that)
      return false;
    }
  }
}
