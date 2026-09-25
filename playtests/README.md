# Weirdkey Playtests

Run a recorded hardware session from the repository root:

```powershell
./gradlew :app:run --args="--hardware --playtest"
```

The command prints the session directory and writes these artifacts beneath `playtests/sessions/<UTC timestamp>/`:

- `events.jsonl`: elapsed time, captured input, LED transitions, capture-set changes, display statuses, failures, and cartridge results.
- `metadata.json`: commit, branch, runtime environment, mode, timestamps, and final status.
- `summary.md`: duration, event counts, input sequence, and cartridge results.
- `notes.md`: prompts for timestamped observation and a short debrief.

The session recorder logs only keys captured by Weirdkey. It does not record arbitrary global typing.

## Suggested Setup

1. Give the tester no explanation beyond how to stop if they ask.
2. Start a keyboard-facing video with think-aloud audio when the tester consents.
3. Put the video filename and participant alias in `notes.md`.
4. Add timestamps when the tester hesitates, verbalizes a theory, asks for help, appears surprised, or considers stopping.
5. Afterward, ask the questions already present in `notes.md` without explaining the intended mechanics first.

For several sessions, use a fresh generated directory each time. Keep raw artifacts private when they contain voice or video; commit only deliberately anonymized findings.

Set `WEIRDKEY_PLAYTEST_DIR` to store sessions outside the repository:

```powershell
$env:WEIRDKEY_PLAYTEST_DIR = "C:\playtests\weirdkey"
```