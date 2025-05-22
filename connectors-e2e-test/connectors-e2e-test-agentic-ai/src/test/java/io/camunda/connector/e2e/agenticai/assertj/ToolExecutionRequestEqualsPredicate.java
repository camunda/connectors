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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;

public class ToolExecutionRequestEqualsPredicate
    implements BiPredicate<ToolExecutionRequest, ToolExecutionRequest> {
  @Override
  public boolean test(ToolExecutionRequest a, ToolExecutionRequest b) {
    if (!Objects.equals(a.id(), b.id())) {
      return false;
    }

    if (!Objects.equals(a.name(), b.name())) {
      return false;
    }

    try {
      JSONCompareResult jsonCompareResult =
          JSONCompare.compareJSON(a.arguments(), b.arguments(), JSONCompareMode.STRICT);
      if (jsonCompareResult.failed()) {
        return false;
      }
    } catch (JSONException e) {
      throw new RuntimeException("Failed to compare JSON", e);
    }

    return true;
  }
}
