package io.camunda.connector.email.exception;

import jakarta.mail.MessagingException;

public class EmailConnectorException extends RuntimeException {
  public EmailConnectorException(MessagingException e) {}
}
