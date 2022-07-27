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
package io.camunda.connector.sendgrid;

import java.util.List;

public class SendGridErrors {

  List<SendGridError> errors;

  public List<SendGridError> getErrors() {
    return errors;
  }

  public void setErrors(final List<SendGridError> errors) {
    this.errors = errors;
  }

  @Override
  public String toString() {
    return "SendGrid returned the following errors: " + String.join("; ", errors);
  }

  static class SendGridError implements CharSequence {
    String message;

    public String getMessage() {
      return message;
    }

    public void setMessage(final String message) {
      this.message = message;
    }

    @Override
    public int length() {
      return message.length();
    }

    @Override
    public char charAt(final int index) {
      return message.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
      return message.subSequence(start, end);
    }

    @Override
    public String toString() {
      return message;
    }
  }
}
