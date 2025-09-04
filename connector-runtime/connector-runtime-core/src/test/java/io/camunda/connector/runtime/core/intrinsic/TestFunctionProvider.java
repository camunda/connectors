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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import jakarta.annotation.Nullable;
import java.nio.charset.Charset;

/** Some test operations that show possible serialization and deserialization use cases. */
public class TestFunctionProvider implements IntrinsicFunctionProvider {

  @IntrinsicFunction(name = "test_documentSize")
  public int documentSize(Document document) {
    return document.asByteArray().length;
  }

  @IntrinsicFunction(name = "test_documentContent")
  public String documentContent(Document document, @Nullable String charset) {
    if (charset == null) {
      return new String(document.asByteArray());
    }
    return new String(document.asByteArray(), Charset.forName(charset));
  }

  @IntrinsicFunction(name = "test_concat")
  public String concat(String a, String b) {
    return a + b;
  }

  private final ObjectMapper objectMapper = new ObjectMapper();

  @IntrinsicFunction(name = "test_anythingToString")
  public String anythingToJson(Object anything) throws JsonProcessingException {
    return objectMapper.writeValueAsString(anything);
  }
}
