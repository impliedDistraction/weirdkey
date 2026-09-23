# weirdkey

Put the game in the controller. weirdkey is a tiny Java runtime for cartridge-style keyboard games where the keyboard topology is the initial world and the monitor is optional.

## What is in the repo now?

- `LogitechG915XTopology` models the first keyboard target as an ordered world map.
- `KeyboardDevice` exposes per-key RGB output plus press / hold / release input events.
- `CartridgeRuntime` runs a cartridge against a keyboard, with an optional display surface.
- `FirstExperimentCartridge` implements the first experiment: light one key, wait for the player to press it, turn it off, then light the next key.

## Run the first experiment

```bash
./gradlew test
./gradlew :app:run --args='ESC F1 HOLD:F1 RELEASE:F1 F1'
```

Bare arguments are treated as key presses; use `PRESS:KEY`, `HOLD:KEY`, or `RELEASE:KEY` to simulate other input events.
