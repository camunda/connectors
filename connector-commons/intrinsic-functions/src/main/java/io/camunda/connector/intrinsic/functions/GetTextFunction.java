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
package io.camunda.connector.intrinsic.functions;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.intrinsic.IntrinsicFunction;
import io.camunda.connector.intrinsic.IntrinsicFunctionProvider;
import jakarta.annotation.Nullable;
import java.nio.charset.Charset;

public class GetTextFunction implements IntrinsicFunctionProvider {

  @IntrinsicFunction(name = "getText")
  public String execute(Document document, @Nullable String charset) {
    final var bytes = document.asByteArray();
    final var charsetInstance =
        charset != null ? Charset.forName(charset) : Charset.defaultCharset();
    return new String(bytes, charsetInstance);
  }
}
