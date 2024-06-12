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
package io.camunda.connector.feel;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import scala.collection.Iterable;
import scala.jdk.javaapi.CollectionConverters;

public class FeelEngineWrapperUtil {
  public static Map<String, Object> wrapResponse(Object response) {
    Map<String, Object> responseContext = new HashMap<>();
    responseContext.put("response", response);
    return responseContext;
  }

  @SuppressWarnings("unchecked")
  public static <T> T sanitizeScalaOutput(T output) {
    if (output instanceof scala.collection.Map<?, ?> scalaMap) {
      return (T)
          CollectionConverters.asJava(scalaMap).entrySet().stream()
              .collect(
                  HashMap::new,
                  (m, v) -> m.put(v.getKey(), sanitizeScalaOutput(v.getValue())),
                  HashMap::putAll);
    } else if (output instanceof Iterable<?> scalaIterable) {
      return (T)
          StreamSupport.stream(CollectionConverters.asJava(scalaIterable).spliterator(), false)
              .map(FeelEngineWrapperUtil::sanitizeScalaOutput)
              .collect(Collectors.toList());
    } else {
      return output;
    }
  }
}
