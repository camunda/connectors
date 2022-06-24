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
