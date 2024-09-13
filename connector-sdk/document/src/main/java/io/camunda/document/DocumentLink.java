package io.camunda.document;

import java.time.ZonedDateTime;

public record DocumentLink(String url, ZonedDateTime expiresAt) {}
