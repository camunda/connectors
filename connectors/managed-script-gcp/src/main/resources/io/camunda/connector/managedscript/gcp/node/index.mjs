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

const TMP = process.env.EXECUTOR_TMP ?? '/tmp';

// An instance keeps /tmp when its server restarts after a timeout or a crash: what a call left
// there must not become part of what the executor keeps. Better not to start than to keep it.
const left = clear(TMP);
if (left.length > 0) {
  throw new Error(`Cannot clear ${TMP} of a previous server: ${left.join(', ')}`);
}

const executor = createExecutor({ local: false, tmp: TMP });

functions.http('handler', async (request, response) => {
  response.json(await executor.run(request.body, FUNCTION_TIMEOUT_MS - SAFETY_MS));
});
