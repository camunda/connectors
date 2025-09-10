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
package io.camunda.connector.runtime.core.intrinsic.functions;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionProvider;
import java.util.Base64;

public class Base64Function implements IntrinsicFunctionProvider {

  @IntrinsicFunction(name = "base64")
  public String execute(Object input) {
    if (input instanceof Document) {
      return ((Document) input).asBase64();
    }
    if (input instanceof String) {
      return Base64.getEncoder().encodeToString(((String) input).getBytes());
    }
    throw new IllegalArgumentException(
        "Unsupported input type: " + input.getClass() + ". Expected Document or String.");
  }
}
