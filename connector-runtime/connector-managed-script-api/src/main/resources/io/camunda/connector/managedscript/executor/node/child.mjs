// Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
// contributor license agreements. Licensed under the Apache License, Version 2.0.
//
// Runs user_script.mjs from the work directory and writes the contract response to stdout.
//
// Started by executor_core in a fresh Node.js process for every call, with a request that
// executor_core has already validated: { variables, context }.

import { fstatSync, writeSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { Console } from 'node:console';

const CONTRACT_VERSION = '1';
const MAX_MESSAGE_LENGTH = 1024;
const MAX_STACK_TRACE_LENGTH = 8192;

// console output in user code goes to the logs, not to the response: every method of the console,
// debug(), dir() and table() included, writes to stderr.
globalThis.console = new Console({ stdout: process.stderr, stderr: process.stderr });

function failure(code, message, stackTrace) {
  const error = { code, message: message.slice(0, MAX_MESSAGE_LENGTH), retryable: false, truncated: false };
  if (stackTrace !== undefined) {
    // The start of a JavaScript stack is the frame nearest the error.
    error.stackTrace = stackTrace.slice(0, MAX_STACK_TRACE_LENGTH);
    error.truncated = stackTrace.length > MAX_STACK_TRACE_LENGTH;
  }
  return { contractVersion: CONTRACT_VERSION, outcome: 'FAILED', error };
}

function scriptError(error) {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  // The stack of an Error can be overwritten with anything: use it only when it is text.
  const stack = error instanceof Error && typeof error.stack === 'string' && error.stack ? error.stack : message;
  return failure('SCRIPT_ERROR', message, stack);
}

const isObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

// A failure of the script that the try/catch below cannot see is still the script's: a timer that
// throws, a promise that rejects with no one waiting, or a process.exit() before execute() returns.
// Without a response the wrapper would report a platform failure and the call would be retried.
let responded = false;
function respondAndExit(response) {
  if (responded) return;
  responded = true;
  writeSync(1, typeof response === 'string' ? response : JSON.stringify(response));
  process.exit(0);
}
process.on('uncaughtException', (error) => respondAndExit(scriptError(error)));
process.on('unhandledRejection', (error) => respondAndExit(scriptError(error)));
// Nothing is added when the script already wrote to stdout itself: the wrapper judges that output.
process.on('exit', (code) => {
  if (!responded && fstatSync(1).size === 0) {
    respondAndExit(failure('SCRIPT_ERROR', `the script ended the process with exit code ${code} before execute() returned`));
  }
});

let input = '';
process.stdin.setEncoding('utf8'); // keeps a character split across two chunks whole
for await (const chunk of process.stdin) input += chunk;
const request = JSON.parse(input);

let response;
try {
  const { execute } = await import(pathToFileURL(path.join(process.argv[2], 'user_script.mjs')).href);
  if (typeof execute !== 'function') throw new TypeError('the script must export a function execute(variables, context)');
  const result = await execute(request.variables, request.context);
  if (!isObject(result)) {
    response = failure('INVALID_RESULT', 'execute() must return a JSON object');
  } else {
    try {
      response = JSON.stringify({ contractVersion: CONTRACT_VERSION, outcome: 'COMPLETED', variables: result });
    } catch (error) {
      response = failure('INVALID_RESULT', `the result of execute() is not JSON: ${error.message}`);
    }
  }
} catch (error) {
  response = scriptError(error);
}

// Exit once the response is written: a timer or socket the script left open must not turn a
// finished call into a timeout, nor write to stdout after the response.
respondAndExit(response);
