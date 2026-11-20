# Report

## Live status (one line per PR, updated each pass)

```
#12345 (main)        📥 enqueued (watching until merged; re-enqueued 0/3)
#12346 (stable/8.7)  🔁 reran run 123 (flake 1/3)
#12347 (stable/8.6)  ✅ merged
#12348 (main)        🟢 ready, awaiting human approval (Copilot 🟢, CI green, 0 open threads)
#12349 (main)        🛠 fixing round 1/2: 2 threads, 1 CI failure
#12350 (main)        ⛔ needs author: conflicting
```

## Final summary

- **Outcome per PR**: merged / ready-awaiting-approval / needs author (an enqueued PR is reported until it merges) — with run or PR links.
- **Copilot**: verdict history per PR (e.g. 🟡 → 🔵 → 🟢), and whether the last one is on the head.
- **Threads** (table): thread URL | author kind | outcome (fixed / declined / follow-up) | reason or link.
  Human threads left open are listed separately under "Needs you".
- **Pushed commits**: sha + subject, one per fix round.
- **Reruns and flakes**: every rerun (check, run link, N/3). A check that flaked repeatedly is a
  hardening candidate — say so.
- **Needs you**: conflicts, capped checks, open human threads, anything ⛔, and the fix-round cap
  if it was hit.
