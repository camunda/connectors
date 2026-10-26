// Local adapter of the Node.js executor: runs one call, read from stdin, and writes the response.
//
// Started by the local provider for every call with EXECUTOR_LOCAL=1, so the wrapper only kills the
// process group of the script, and EXECUTOR_TMP is a directory of its own for the call.
import { run } from './executor_core.mjs';

const DEADLINE_MS = Number(process.env.EXECUTOR_DEADLINE_MS || 30_000);

let input = '';
for await (const chunk of process.stdin) input += chunk;
process.stdout.write(JSON.stringify(await run(JSON.parse(input), DEADLINE_MS)));
