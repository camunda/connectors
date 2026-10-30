// Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
// contributor license agreements. Licensed under the Apache License, Version 2.0.
//
// Test driver of the Node.js wrapper core: runs one call, read from stdin, in local mode, and writes
// the contract response to stdout. The local provider ships its own entry point; this one only
// lets the wrapper tests exercise the core without depending on a provider.

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
