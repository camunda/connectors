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

import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * One entry of an {@value SuppressionFile#FILE_NAME} file.
 *
 * @param file the suppression file declaring this entry
 * @param pointer JSON pointer of this entry inside {@code file}
 * @param rule id of the suppressed rule
 * @param template glob relative to the module's {@code element-templates/} directory
 * @param property property id the suppression is limited to, or {@code null} for the whole template
 * @param reason why the finding is accepted
 */
public record Suppression(
    Path file,
    String pointer,
    String rule,
    String template,
    String property,
    String reason,
    PathMatcher templateMatcher) {}
