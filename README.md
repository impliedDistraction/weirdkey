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

Weirdkey saves the current lighting, turns the keyboard dark, and lights one key green. Captured gameplay keys stay inside Weirdkey instead of reaching the foreground Windows app, while Esc remains available to quit and restore the saved lighting. Set `WEIRDKEY_LOGITECH_LED_DLL` only if G HUB's LED SDK DLL is installed outside its standard location.

## Runtime events

Cartridges can use `GameContext.events()` to publish and subscribe without adding device-specific callbacks. Events carry their emitter and tags, subscriptions can expire after a fixed number of deliveries, and completed emissions can chain immediate or delayed follow-ups:

```java
EventTag gameplay = new EventTag("gameplay");

context.events().subscribeTimes(KeyInputEvent.class, 3, envelope -> {
	KeyboardDevice keyboard = envelope.emitterAs(KeyboardDevice.class).orElseThrow();
	System.out.println(keyboard.topology());
});

context.events()
	.emit(new InvalidAction("Expected F4"), this, gameplay)
	.thenEmitAfter(Duration.ofMillis(150), new FeedbackFinished());
```

Dispatch is ordered and fail-fast. Delayed events are serialized with immediate events, and closing the cartridge runtime cancels pending delayed emissions.

## Runtime lifecycle

Every top-level event runs through a deterministic cycle:

```text
PRE_UPDATE -> UPDATE -> POST_UPDATE -> COMMIT -> apply device output
```

Event subscribers and `Cartridge.onInput(...)` run during UPDATE. Cartridge state remains ordinary Java state owned by the cartridge. Phase callbacks can observe it at explicit boundaries:

```java
context.onPhase(LifecyclePhase.PRE_UPDATE, validation::check);
context.onPhase(LifecyclePhase.UPDATE, movement::update);
context.onPhase(LifecyclePhase.POST_UPDATE, console::inspect);
```

Calls such as `lightKey`, `clearKey`, and `showStatus` are buffered until all COMMIT callbacks finish. POST_UPDATE therefore sees completed logical state before physical or display output changes. Reentrant events remain in the current UPDATE; delayed events begin a fresh cycle. A callback failure aborts later phases and discards buffered output for that cycle.
