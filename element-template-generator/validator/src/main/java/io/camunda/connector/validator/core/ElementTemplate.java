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
package io.camunda.connector.validator.core;

/** JSON field names used in the Camunda element-template schema. */
public final class ElementTemplate {

  private ElementTemplate() {}

  public static final String PROPERTIES = "properties";
  public static final String CONFIGURATION_TEMPLATES = "configurationTemplates";
  public static final String GROUPS = "groups";
  public static final String ID = "id";
  public static final String VERSION = "version";

  public static final String CHOICES = "choices";
  public static final String VALUE = "value";
  public static final String TYPE = "type";
  public static final String GROUP = "group";

  public static final String CONDITION = "condition";
  public static final String PROPERTY = "property";
  public static final String EQUALS = "equals";
  public static final String ONE_OF = "oneOf";
  public static final String ALL_MATCH = "allMatch";

  public static final String PRESETS = "presets";
  public static final String BINDING = "binding";

  public static final String STEPS = "steps";
  public static final String PRESET_ID = "presetId";
  public static final String KEYWORDS = "keywords";
  public static final String NAME = "name";
  public static final String DESCRIPTION = "description";
}
