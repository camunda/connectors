// Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
// under one or more contributor license agreements. Licensed under a proprietary license.
// See the License.txt file for more information. You may not use this file
// except in compliance with the proprietary license.
//
// AWS Lambda entry point of the Node.js executor. The executor is created once per instance, when
// Lambda loads this module, so that what exists at that moment is kept after every call and
// anything else is removed.

import { clear, createExecutor } from './executor_core.mjs';

// Left to the wrapper, out of the remaining time of the function, to answer and clean up.
const SAFETY_MS = 1_000;

// The memory of the function, set by Lambda: the script gets it but for a margin.
const MEMORY_MB = Number(process.env.AWS_LAMBDA_FUNCTION_MEMORY_SIZE);
if (!(Number.isInteger(MEMORY_MB) && MEMORY_MB > 0)) {
  throw new Error('AWS_LAMBDA_FUNCTION_MEMORY_SIZE must be a positive number of MB');
}

const TMP = process.env.EXECUTOR_TMP ?? '/tmp';

// Lambda keeps /tmp when it restarts the runtime after a timeout or a crash: what a call left there
// must not become part of what the executor keeps. Better not to start than to keep it.
const left = clear(TMP);
if (left.length > 0) {
  throw new Error(`Cannot clear ${TMP} of a previous runtime: ${left.join(', ')}`);
}

const executor = createExecutor({ local: false, tmp: TMP, memoryMb: MEMORY_MB });

export const handler = async (event, context) =>
  executor.run(event, context.getRemainingTimeInMillis() - SAFETY_MS);
