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
package io.camunda.connector.feel.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.LocalFeelEngineWrapper;
import java.util.function.Function;
import java.util.function.Supplier;

public class JacksonModuleFeelFunction extends SimpleModule {

  /**
   * Evaluator used for {@code @FEEL}-annotated fields (via {@link FeelAnnotationIntrospector}).
   * These fields typically represent connector properties that may reference cluster variables
   * (e.g., {@code camunda.vars.env.*}), so this evaluator may use cluster-based evaluation.
   */
  private final FeelExpressionEvaluator annotationEvaluator;

  /**
   * Evaluator used for {@link Function} and {@link Supplier} deserializers. These types represent
   * runtime transformations that operate on in-process data (which may include Documents or other
   * non-serializable objects), so this evaluator should use local evaluation.
   */
  private final FeelExpressionEvaluator functionEvaluator;

  /**
   * Using this flag, the module can be configured to not process the {@code @FEEL} annotation. This
   * can be useful in scenarios where only deserialization of Function/Supplier is needed, but not
   * the annotation processing (e.g., to avoid interference with other modules). This way, we can
   * use the same models with (inbound connectors) and without (outbound connectors) FEEL support.
   */
  private final boolean processFEELAnnotation;

  /** Creates a module using local FEEL engine for all evaluations. */
  public JacksonModuleFeelFunction() {
    this(true, new LocalFeelEngineWrapper(), null);
  }

  /**
   * Creates a module with the specified FEEL expression evaluator used for all evaluation types.
   *
   * @param processFEELAnnotation whether to process @FEEL annotations
   * @param evaluator the FEEL expression evaluator to use for all evaluations
   */
  public JacksonModuleFeelFunction(
      boolean processFEELAnnotation, FeelExpressionEvaluator evaluator) {
    this(processFEELAnnotation, evaluator, null);
  }

  /**
   * Creates a module with separate evaluators for annotation-driven and type-driven
   * deserialization.
   *
   * @param processFEELAnnotation whether to process @FEEL annotations
   * @param annotationEvaluator evaluator for {@code @FEEL}-annotated fields (may use cluster
   *     evaluation for access to cluster variables)
   * @param functionEvaluator evaluator for {@link Function}/{@link Supplier} fields (should use
   *     local evaluation to avoid serializing runtime objects like Documents). If null, defaults to
   *     a local FEEL engine.
   */
  public JacksonModuleFeelFunction(
      boolean processFEELAnnotation,
      FeelExpressionEvaluator annotationEvaluator,
      FeelExpressionEvaluator functionEvaluator) {
    this.processFEELAnnotation = processFEELAnnotation;
    this.annotationEvaluator =
        annotationEvaluator != null ? annotationEvaluator : new LocalFeelEngineWrapper();
    this.functionEvaluator =
        functionEvaluator != null ? functionEvaluator : new LocalFeelEngineWrapper();
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
