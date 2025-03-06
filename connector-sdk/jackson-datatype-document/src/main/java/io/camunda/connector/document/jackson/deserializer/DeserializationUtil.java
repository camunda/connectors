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

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.IntrinsicFunctionModel;
import java.io.IOException;

public class DeserializationUtil {

  public static boolean isDocumentReference(JsonNode node) {
    return node.has(DocumentReferenceModel.DISCRIMINATOR_KEY);
  }

  public static boolean isOperation(JsonNode node) {
    return node.has(IntrinsicFunctionModel.DISCRIMINATOR_KEY);
  }

  public static DocumentReferenceModel readAsDocumentReference(
      JsonNode node, DeserializationContext ctx) throws IOException {

    if (!isDocumentReference(node)) {
      throw new IllegalArgumentException(
          "Unsupported document format. Expected a document reference, got: " + node);
    }
    return ctx.readTreeAsValue(node, DocumentReferenceModel.class);
  }

  public static IntrinsicFunctionModel readAsOperation(JsonNode node, DeserializationContext ctx)
      throws IOException {

    if (!isOperation(node)) {
      throw new IllegalArgumentException(
          "Unsupported document format. Expected an operation, got: " + node);
    }
    return ctx.readTreeAsValue(node, IntrinsicFunctionModel.class);
  }
}
