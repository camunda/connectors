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
package io.camunda.connector.document.jackson.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import java.io.IOException;

/** Base class for deserializers within the document module. */
public abstract class AbstractDeserializer<T> extends JsonDeserializer<T> {

  protected final DocumentModuleSettings settings;

  public AbstractDeserializer(DocumentModuleSettings settings) {
    this.settings = settings;
  }

  /**
   * Base method from {@link JsonDeserializer} to deserialize a JSON node. It will delegate to
   * {@link #handleJsonNode(JsonNode, DeserializationContext)} to perform the actual
   * deserialization.
   */
  @Override
  public T deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    DeserializationUtil.ensureIntrinsicFunctionCounterInitialized(deserializationContext, settings);
    final JsonNode node = jsonParser.readValueAsTree();
    if (node == null || node.isNull()) {
      return null;
    }
    return handleJsonNode(node, deserializationContext);
  }

  /**
   * Deserialize from a JSON node. This method is used to cross-reference the deserialization logic
   * from another deserializer, when the JSON node has already been read form the parser, because
   * the parser can only be read once. All deserializer-specific logic should be implemented in this
   * method.
   */
  protected abstract T handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException;
}
