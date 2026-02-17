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
package io.camunda.connector.generator.java.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enables element template generation for a connector */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ElementTemplate {

  /** Element template ID. Must be unique among the connector templates used in your project. */
  String id();

  /** Element template name. Will be displayed in Camunda Modeler as a label for the connector. */
  String name();

  /**
   * Reference to the connector input data class. Element template is generated based on the
   * properties of this class.
   */
  Class<?> inputDataClass() default Void.class;

  Class<?> outputDataClass() default Void.class;

  /**
   * List of optional extension properties to be added to the element template as hidden properties.
   */
  ExtensionProperty[] extensionProperties() default {};

  /**
   * Element template version. The version should be incremented every time the template is changed
   * to make use of the version upgrade mechanism in Camunda Modeler.
   *
   * <p>If not specified, the default value is 0.
   *
   * <p><b>NB</B>: It is recommended to specify the version explicitly for element templates used in
   * production.
   */
  int version() default 0;

  /**
   * Link to the documentation page for the connector. Will be used by the Camunda Modeler to
   * redirect users to the documentation page when they click on the corresponding button.
   *
   * <p>If not specified, the button will not be displayed.
   */
  String documentationRef() default "";

  /** Semantic version range */
  String engineVersion() default "";

  /**
   * Element template description. Will be displayed along with the connector name in Camunda
   * Modeler.
   *
   * <p>If not specified, the description will be empty.
   */
  String description() default "";

  Metadata metadata() default @Metadata;

  /**
   * Manual configuration for the connector property groups.
   *
   * <p>By default, the property groups are generated from the connector's input data class based on
   * the data provided in the {@link TemplateProperty} annotations. Group IDs and labels are
   * generated from the field names. The default property order is undefined.
   *
   * <p>Use this property to override the default behavior and provide custom labels or ordering.
   * The order of the property groups in the array defines the order in which they will be displayed
   * in Camunda Modeler.
   */
  PropertyGroup[] propertyGroups() default {};

  /**
   * Icon for the connector. Will be displayed in Camunda Modeler along with the connector name.
   * Should be a classpath resource path. The classpath resource should either be an SVG or PNG
   * image.
   *
   * <p>It is recommended to use squared SVG graphics. The icons get rendered 18x18 pixels in the
   * element on the modeling canvas, and 32x32 pixels in the properties panel.
   */
  String icon() default "";

  ConnectorElementType[] elementTypes() default {};

  /**
   * Default result variable value.
   *
   * <p>If not specified, no default variable value will be set.
   */
  String defaultResultVariable() default "";

  /**
   * Default result expression value.
   *
   * <p>If not specified, no default expression value will be set.
   */
  String defaultResultExpression() default "";

  /** Metadata tags for the connector. Will be used in Camunda Modeler. */
  @interface Metadata {

    String[] keywords() default {};
  }

  @interface PropertyGroup {

    String id();

    String label() default "";

    String tooltip() default "";

    boolean openByDefault() default true;
  }

  @interface ExtensionProperty {
    String name();

    String value();

    TemplateProperty.PropertyCondition condition() default
        @TemplateProperty.PropertyCondition(property = "");
  }

  @interface ConnectorElementType {

    /**
     * BPMN element types the template is applicable to, e.g. "bpmn:Task".
     *
     * <p>If not specified, the default value is chosen by the underlying generator implementation.
     *
     * @see #elementType() allows to configure the resulting element type.
     */
    BpmnType[] appliesTo() default {};

    /**
     * The target element types of the template, e.g. "bpmn:ServiceTask". When template is applied,
     * the element is transformed to this type.
     *
     * <p>Multiple element types are supported. If not specified, the default value is chosen by the
     * underlying generator implementation. If another value is specified in
     * {io.camunda.connector.generator.api.GeneratorConfiguration#elementTypes()}, the value from
     * the configuration will be used.
     *
     * @see #appliesTo() allows to configure the types the template can be applied to.
     */
    BpmnType elementType();

    /**
     * If set, overrides the element template name for this element type. If not set, the value from
     * the annotation on the class level is used by default (see {@link ElementTemplate#name()}),
     * suffixed with the element type short name, e.g. "My Connector (Service Task)".
     */
    String templateNameOverride() default "";

    /**
     * If set, overrides the element template ID for this element type. If not set, the value from
     * the annotation on the class level is used by default (see {@link ElementTemplate#id()}),
     * suffixed with the element type short name, e.g. "my-connector:ServiceTask".
     */
    String templateIdOverride() default "";
  }
}
