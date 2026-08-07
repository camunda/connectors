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
package io.camunda.connector.feel.jackson.v2;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Jackson 2 counterpart of {@link io.camunda.connector.feel.jackson.JacksonModuleFeelFunction}.
 * Used only by the {@code camundaJsonMapper} Spring bean — see {@link AbstractFeelDeserializer}'s
 * javadoc for why this fork exists. Every other ObjectMapper uses the Jackson 3 variant instead.
 */
public class JacksonModuleFeelFunction extends SimpleModule {

  private final FeelExpressionEvaluator annotationEvaluator;
  private final FeelExpressionEvaluator functionEvaluator;
  private final boolean processFEELAnnotation;

  /** Creates a module using local FEEL engine for all evaluations. */
  public JacksonModuleFeelFunction() {
    this(true, new LocalFeelExpressionEvaluator(), null);
  }

  public JacksonModuleFeelFunction(
      boolean processFEELAnnotation, FeelExpressionEvaluator evaluator) {
    this(processFEELAnnotation, evaluator, null);
  }

  public JacksonModuleFeelFunction(
      boolean processFEELAnnotation,
      FeelExpressionEvaluator annotationEvaluator,
      FeelExpressionEvaluator functionEvaluator) {
    this.processFEELAnnotation = processFEELAnnotation;
    this.annotationEvaluator =
        annotationEvaluator != null ? annotationEvaluator : new LocalFeelExpressionEvaluator();
    this.functionEvaluator =
        functionEvaluator != null ? functionEvaluator : new LocalFeelExpressionEvaluator();
  }

  @Override
  public String getModuleName() {
    return "JacksonModuleFeelFunction";
  }

  @Override
  public Version version() {
    // TODO: get version from pom.xml
    return new Version(0, 1, 0, null, "io.camunda", "jackson-datatype-feel");
  }

  @Override
  public void setupModule(SetupContext context) {
    addDeserializer(
        Function.class,
        new FeelFunctionDeserializer<>(TypeFactory.unknownType(), functionEvaluator));
    addDeserializer(
        Supplier.class,
        new FeelSupplierDeserializer<>(TypeFactory.unknownType(), functionEvaluator));
    if (processFEELAnnotation) {
      context.insertAnnotationIntrospector(new FeelAnnotationIntrospector(annotationEvaluator));
    }
    super.setupModule(context);
  }
}
