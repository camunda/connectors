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

import io.camunda.connector.api.document.DocumentReturnChoice;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated input class (or sealed sub-type) exposes the user-selectable response
 * format dropdown. The element template generator emits a {@code documentReturnFormat} dropdown
 * property with the supported choices (Document reference / as text / as JSON) plus a conditional
 * encoding sub-property that appears only when {@code TEXT} is selected.
 *
 * <p>The user-visible dropdown label is "Response format" by default — the Java vocabulary uses
 * "Return" to match the connector-facing {@link io.camunda.connector.api.document.DocumentReturn}
 * type, while the Modeler label stays user-friendly.
 *
 * <p>Bindings are always root-level: the dropdown binds to {@code documentReturnFormat.choice} and
 * the encoding to {@code documentReturnFormat.encoding}, regardless of whether the annotation sits
 * on a top-level input class (e.g. HTTP) or a sealed sub-type (e.g. S3 {@code DownloadObject}, GCS
 * / Azure / GoogleDrive download sub-types). The runtime reads from these same paths via {@link
 * io.camunda.connector.api.outbound.OutboundConnectorContext#readDocumentReturnFormat()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DocumentReturnFormat {

  /** Label shown on the dropdown. */
  String label() default "Response format";

  /** Description shown on the dropdown. */
  String description() default "";

  /** Group ID for the property. All generated sub-properties inherit this group. */
  String group() default "";

  /**
   * Optional visibility condition. Use this for non-sealed nested records (e.g. Google Drive's
   * {@code DownloadData}) where the discriminator condition can't be derived from a sealed
   * sub-type. For sealed sub-types, the discriminator condition is added automatically and this
   * attribute is unnecessary.
   */
  TemplateProperty.PropertyCondition condition() default
      @TemplateProperty.PropertyCondition(property = "");

  /** Tooltip for the property. */
  String tooltip() default "";

  /**
   * Choices made available in the dropdown. The order is preserved in the rendered UI. Defaults to
   * all three formats; restrict (e.g. to {@code {DOCUMENT, TEXT}}) when one of the choices does not
   * make sense for the connector.
   */
  DocumentReturnChoice[] supportedFormats() default {
    DocumentReturnChoice.DOCUMENT, DocumentReturnChoice.TEXT, DocumentReturnChoice.JSON
  };

  /** Default selection. Must be one of {@link #supportedFormats()}. */
  DocumentReturnChoice defaultFormat() default DocumentReturnChoice.DOCUMENT;

  /** Visibility of the encoding sub-property. Only shown when the user selects TEXT. */
  FieldVisibility encoding() default FieldVisibility.OPTIONAL;

  /** Default value for the encoding sub-property when shown. */
  String defaultEncoding() default "UTF-8";
}
