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

import static io.camunda.connector.feel.JacksonSupport.MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import java.util.Optional;
import java.util.function.Function;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.valuemapper.JavaCustomValueMapper;

public class CustomValueMapper extends JavaCustomValueMapper {
  private final ObjectMapper objectMapper;

  public CustomValueMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<Val> toValue(Object object, Function<Object, Val> innerValueMapper) {
    // TODO: Refactor in scope of https://github.com/camunda/team-connectors/issues/964
    if (object instanceof Document document) {
      // Make sure that documents are converted to references before accessing them in FEEL
      // expressions
      return Optional.of(
          innerValueMapper.apply(
              objectMapper.convertValue(document.reference(), MAP_TYPE_REFERENCE)));
    } else return Optional.empty();
  }

  @Override
  public Optional<Object> unpackValue(Val value, Function<Val, Object> innerValueMapper) {
    return Optional.empty();
  }
}
