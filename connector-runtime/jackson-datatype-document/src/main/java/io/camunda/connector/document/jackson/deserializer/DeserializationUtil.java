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
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;

public class DeserializationUtil {

  public static final String INTRINSIC_FUNCTION_LIMIT_REMAINING = "IntrinsicFunctionLimitRemaining";

  public static boolean isDocumentReference(JsonNode node) {
    return node.has(DocumentReferenceModel.DISCRIMINATOR_KEY);
  }

  public static boolean isIntrinsicFunction(JsonNode node) {
    return node.has(IntrinsicFunctionModel.DISCRIMINATOR_KEY);
  }

  public static void ensureIntrinsicFunctionCounterInitialized(
      DeserializationContext ctxt, DocumentModuleSettings settings) {
    if (ctxt.getAttribute(INTRINSIC_FUNCTION_LIMIT_REMAINING) == null) {
      ctxt.setAttribute(INTRINSIC_FUNCTION_LIMIT_REMAINING, settings.getMaxIntrinsicFunctions());
    }
  }

  public static void tryDecrementIntrinsicFunctionCounter(DeserializationContext ctxt) {
    final Integer remaining = (Integer) ctxt.getAttribute(INTRINSIC_FUNCTION_LIMIT_REMAINING);
    if (remaining == null) {
      throw new IllegalStateException("Intrinsic function counter not initialized");
    }
    if (remaining <= 0) {
      throw new IllegalStateException("Intrinsic function limit exceeded");
    }
    // thread-safe because the context object is not shared
    ctxt.setAttribute(INTRINSIC_FUNCTION_LIMIT_REMAINING, remaining - 1);
  }
}
