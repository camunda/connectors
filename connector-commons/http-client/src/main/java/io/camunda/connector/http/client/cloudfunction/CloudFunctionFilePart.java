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
package io.camunda.connector.http.client.cloudfunction;

import io.camunda.connector.http.client.ExecutionEnvironment;

/**
 * Represents a part of a multipart form submission. Can contain either files or a fields. This
 * record prevents any dependency on the Servlet API. This part is used only in the {@link
 * ExecutionEnvironment.SaaSCloudFunction} environment.
 *
 * @param name The name of the field in the multipart form corresponding to this part.
 * @param fileName If this part represents an uploaded file, gets the file name submitted in the
 *     upload. Returns {@code null} if no file name is available or if this part is not a file
 *     upload.
 * @param content The content of this part.
 * @param contentType The content type of this part.
 */
public record CloudFunctionFilePart(
    String name, String fileName, byte[] content, String contentType) {}
