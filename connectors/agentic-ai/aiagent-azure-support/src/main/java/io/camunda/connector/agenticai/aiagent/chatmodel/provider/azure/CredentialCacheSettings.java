/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure;

import java.time.Duration;

/**
 * How the Microsoft Entra ID token credentials are cached.
 *
 * @param enabled whether credentials are cached at all
 * @param maximumSize the number of credentials the cache holds
 * @param expireAfterAccess how long a credential stays cached after its last use
 */
public record CredentialCacheSettings(
    boolean enabled, long maximumSize, Duration expireAfterAccess) {}
