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
package io.camunda.intrinsic.functions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.intrinsic.IntrinsicFunction;
import io.camunda.intrinsic.IntrinsicFunctionProvider;
import java.io.IOException;
import java.util.Map;
import javax.annotation.Nullable;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.FeelEngine.Failure;
import org.camunda.feel.impl.JavaValueMapper;
import scala.util.Either;

public class GetJsonFunction implements IntrinsicFunctionProvider {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FeelEngine feelEngine =
      new FeelEngine.Builder().customValueMapper(new JavaValueMapper()).build();

  @IntrinsicFunction(name = "getJson")
  public Object execute(Document document, @Nullable String feelExpression) {
    try {
      Map<String, Object> json =
          objectMapper.readValue(document.asByteArray(), new TypeReference<>() {});
      if (feelExpression == null || feelExpression.isBlank()) {
        return json;
      }
      // FEEL expects variables as a context map, so wrap the JSON as a variable
      Either<Failure, Object> result = feelEngine.evalExpression(feelExpression, json);
      if (result.isLeft()) {
        throw new RuntimeException(result.left().get().message());
      }
      return result.right().get();
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse document as JSON", e);
    }
  }
}
