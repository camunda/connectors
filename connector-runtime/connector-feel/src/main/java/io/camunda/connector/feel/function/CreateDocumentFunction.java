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
package io.camunda.connector.feel.function;

import static io.camunda.connector.feel.FeelConnectorFunctionProvider.RESULT_FUNCTION_TYPE_PROPERTY;
import static io.camunda.connector.feel.FeelConnectorFunctionProvider.currentCreateDocumentNonce;

import java.util.HashMap;
import java.util.List;
import org.camunda.feel.context.Context;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValContext;
import scala.collection.JavaConverters;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;

/**
 * FEEL function {@code createDocument(value)}. It only tags {@code value} with a sentinel
 * discriminator so it survives JSON serialization intact — actual document creation happens later,
 * when {@code io.camunda.connector.runtime.core.document.ResultDocumentResolver} walks the
 * evaluated result/error expression tree and finds this sentinel. This function has no access to a
 * {@code DocumentFactory} and must not attempt to create anything itself. The discriminator's value
 * is read from {@link
 * io.camunda.connector.feel.FeelConnectorFunctionProvider#currentCreateDocumentNonce()}, scoped to
 * the currently evaluating expression rather than a per-JVM constant.
 */
public class CreateDocumentFunction {

  public static final String NAME = "createDocument";

  private static final List<String> ARGUMENTS = List.of("value");

  private static final JavaFunction FUNCTION =
      new JavaFunction(ARGUMENTS, args -> createContext(args.get(0)));

  public static final List<JavaFunction> FUNCTIONS = List.of(FUNCTION);

  private static ValContext createContext(Val value) {
    java.util.Map<String, Object> javaMap = new HashMap<>();
    javaMap.put(RESULT_FUNCTION_TYPE_PROPERTY, currentCreateDocumentNonce());
    javaMap.put("value", value);
    return new ValContext(
        new Context.StaticContext(Map.from(JavaConverters.asScala(javaMap)), Map$.MODULE$.empty()));
  }
}
