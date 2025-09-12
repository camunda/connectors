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
package io.camunda.connector.runtime;

<<<<<<< HEAD:connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/TestValidation.java
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;

public record TestValidation(
    @Digits(fraction = 0, integer = 2) String test, @NotEmpty String test2) {}
=======
import io.camunda.connector.http.client.client.apache.CustomHttpBody.BytesBody;
import io.camunda.connector.http.client.client.apache.CustomHttpBody.StringBody;

public sealed interface CustomHttpBody permits BytesBody, StringBody {
  record BytesBody(byte[] value) implements CustomHttpBody {}

  record StringBody(String value) implements CustomHttpBody {}
}
>>>>>>> e048b31f0 (feat(external-document): Improvments for external document):connector-commons/http-client/src/main/java/io/camunda/connector/http/client/client/apache/CustomHttpBody.java
