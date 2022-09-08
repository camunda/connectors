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
package io.camunda.connector.impl;

import io.camunda.connector.api.annotation.Secret;

class TestInput {
  public final String finalField = "final";
  public String publicField = "public";
  protected String protectedField = "protected";
  String packageField = "package";
  private String privateField = "private";

  @Secret private String secretField = "secrets.s3cr3t";

  public String getPublicField() {
    return publicField;
  }

  public void setPublicField(String publicField) {
    this.publicField = publicField;
  }

  public String getProtectedField() {
    return protectedField;
  }

  public void setProtectedField(String protectedField) {
    this.protectedField = protectedField;
  }

  public String getPrivateField() {
    return privateField;
  }

  public void setPrivateField(String privateField) {
    this.privateField = privateField;
  }

  public String getFinalField() {
    return finalField;
  }

  public String getPackageField() {
    return packageField;
  }

  public void setPackageField(String packageField) {
    this.packageField = packageField;
  }

  public String getSecretField() {
    return secretField;
  }

  public void setSecretField(String secretField) {
    this.secretField = secretField;
  }
}
