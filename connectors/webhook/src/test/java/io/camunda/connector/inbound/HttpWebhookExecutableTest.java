/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpWebhookExecutableTest {

  @Mock private ObjectMapper mapper;

  private HttpWebhookExecutable testObject;

  @BeforeEach
  void beforeEach() {
    testObject = new HttpWebhookExecutable(mapper);
  }

  @Test
  void triggerWebhook_NoHMACAndJSONBody_HappyCase() {}

  @Test
  void triggerWebhook_HMACEnabled_HappyCase() {}

  @Test
  void triggerWebhook_HMACInvalid_RaisesException() {}

  @Test
  void triggerWebhook_FormDataBody_BodyParsed() {}

  @Test
  void triggerWebhook_NoContentTypeAndBodyAsJson_BodyParsed() {}

  @Test
  void triggerWebhook_NonRegularContentTypeAndBodyAsJson_bodyParsed() {}
}
