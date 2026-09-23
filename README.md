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

## Run on a G915 X on Windows

Install Logitech G HUB, connect the keyboard, and run:

```powershell
./gradlew :app:run --args="--hardware"
```

Weirdkey saves the current lighting, turns the keyboard dark, and lights one key green. Press the green key to advance; press Esc to quit and restore the saved lighting. Set `WEIRDKEY_LOGITECH_LED_DLL` only if G HUB's LED SDK DLL is installed outside its standard location.
