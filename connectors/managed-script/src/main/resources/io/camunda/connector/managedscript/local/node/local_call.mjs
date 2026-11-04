// Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
// under one or more contributor license agreements. Licensed under a proprietary license.
// See the License.txt file for more information. You may not use this file
// except in compliance with the proprietary license.
//
// Local entry point of the Node.js executor: runs one call, read from stdin, and writes the
// contract response to stdout. Always in local mode, so it only ever kills the process group of
// its own script. Every call gets a directory of its own under EXECUTOR_TMP, or the system's
// temporary directory, and the directory is removed after the call.

import { tmpdir } from 'node:os';
import { createExecutor } from './executor_core.mjs';

// No platform deadline on a local host: the request's timeoutMs alone bounds the script.
const DEADLINE_MS = Number(process.env.EXECUTOR_DEADLINE_MS || Infinity);

const executor = createExecutor({ local: true, tmp: process.env.EXECUTOR_TMP || tmpdir() });

let input = '';
process.stdin.setEncoding('utf8'); // keeps a character split across two chunks whole
for await (const chunk of process.stdin) input += chunk;
let event;
try {
  event = JSON.parse(input);
} catch {
  event = null;
}
process.stdout.write(JSON.stringify(await executor.run(event, DEADLINE_MS)));
