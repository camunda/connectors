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

/**
 * Where a document input can come from. Used by {@link TemplateDocumentProperty#sources()} to
 * restrict the generated source dropdown for connectors that cannot consume every source.
 */
public enum DocumentSource {

  /** A reference to a document in the Camunda document store. */
  CAMUNDA("Camunda Document", "camunda"),

  /**
   * Content typed directly into the template. Note that inline content is turned into bytes as
   * UTF-8 text, so connectors that require binary input (e.g. AWS Textract, which only accepts
   * PDF/PNG/JPEG/TIFF) should leave this source out.
   */
  INLINE("Inline Content", "inline"),

  /** A document fetched from an external URL. */
  EXTERNAL("From URL", "external");

  private final String label;
  private final String value;

  DocumentSource(String label, String value) {
    this.label = label;
    this.value = value;
  }

  /** Human-readable label shown in the Modeler dropdown generated for this source. */
  public String getLabel() {
    return label;
  }

  /**
   * Value the dropdown writes, and what the generated conditions and composer expression compare
   * against. Part of the template contract: changing it breaks existing models.
   */
  public String getValue() {
    return value;
  }
}
