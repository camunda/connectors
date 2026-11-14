// Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
// under one or more contributor license agreements. Licensed under a proprietary license.
// See the License.txt file for more information. You may not use this file
// except in compliance with the proprietary license.
//
// Cloud Run functions entry point of the Node.js executor. The executor is created once per
// instance, when the Functions Framework loads this module, so that what exists at that moment is
// kept after every call and anything else is removed. The function serves one request at a time:
// the cleanup after a call would kill a concurrent script.

import functions from '@google-cloud/functions-framework';
import { clear, createExecutor } from './executor_core.mjs';

// Left to the wrapper, out of the function timeout, to answer and clean up.
const SAFETY_MS = 1_000;

// Cloud Run gives no remaining time: the provider sets the function timeout.
const FUNCTION_TIMEOUT_MS = Number(process.env.EXECUTOR_FUNCTION_TIMEOUT_MS);
if (!(FUNCTION_TIMEOUT_MS > 0)) {
  throw new Error('EXECUTOR_FUNCTION_TIMEOUT_MS must be a positive number of milliseconds');
}

// The memory of the instance, set by the provider: the script gets it but for a margin.
const MEMORY_MB = Number(process.env.EXECUTOR_MEMORY_MB);
if (!(Number.isInteger(MEMORY_MB) && MEMORY_MB > 0)) {
  throw new Error('EXECUTOR_MEMORY_MB must be a positive number of MB');
}

const TMP = process.env.EXECUTOR_TMP ?? '/tmp';

// Whatever is in /tmp when the module loads becomes part of what the executor keeps. A new container
// starts with an empty /tmp, but for entries of the platform, such as "run", that cannot be removed:
// they are kept. Anything a previous server of the instance left that can be removed is removed.
const left = clear(TMP);
if (left.length > 0) {
  console.error(`Kept in ${TMP}, which the server cannot remove: ${left.join(', ')}`);
}

const executor = createExecutor({ local: false, tmp: TMP, memoryMb: MEMORY_MB });

functions.http('handler', async (request, response) => {
  response.json(await executor.run(request.body, FUNCTION_TIMEOUT_MS - SAFETY_MS));
});
