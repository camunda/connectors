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
package io.camunda.connector.runtime.core.intrinsic;

import java.util.function.Supplier;
import tools.jackson.databind.ObjectMapper;

/**
 * Breaks the circular construction between an {@link ObjectMapper} and its document-deserializer
 * module: the module needs an {@link DefaultIntrinsicFunctionExecutor} that can bind intrinsic
 * function parameters through the very mapper the module is being registered onto. Jackson 3's
 * {@code ObjectMapper} is immutable, so that final, fully-configured mapper does not exist yet when
 * the executor is constructed. Callers construct this holder first, build the executor and
 * deserializer module from it, build the final mapper, then call {@link #set} with it.
 */
public class MutableObjectMapperSupplier implements Supplier<ObjectMapper> {

  private ObjectMapper mapper;

  @Override
  public ObjectMapper get() {
    if (mapper == null) {
      throw new IllegalStateException("ObjectMapper not yet set on this supplier");
    }
    return mapper;
  }

  public void set(ObjectMapper mapper) {
    this.mapper = mapper;
  }
}
