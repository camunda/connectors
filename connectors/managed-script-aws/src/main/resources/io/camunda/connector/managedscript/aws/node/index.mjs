// Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
// under one or more contributor license agreements. Licensed under a proprietary license.
// See the License.txt file for more information. You may not use this file
// except in compliance with the proprietary license.
//
// AWS Lambda entry point of the Node.js executor. The executor is created once per instance, when
// Lambda loads this module, so that what exists at that moment is kept after every call and
// anything else is removed.

import { createExecutor } from './executor_core.mjs';

// Left to the wrapper, out of the remaining time of the function, to answer and clean up.
const SAFETY_MS = 1_000;

const executor = createExecutor({ local: false, tmp: '/tmp' });

export const handler = async (event, context) =>
  executor.run(event, context.getRemainingTimeInMillis() - SAFETY_MS);
