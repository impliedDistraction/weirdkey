# weirdkey

Put the game in the controller. weirdkey is a tiny Java runtime for cartridge-style keyboard games where the keyboard topology is the initial world and the monitor is optional.

## What is in the repo now?

- `LogitechG915XTopology` models the first keyboard target as an ordered world map.
- `KeyboardDevice` exposes per-key RGB output plus press / hold / release input events.
- `CartridgeRuntime` installs a cartridge's event and lifecycle composition against a keyboard, with an optional display surface.
- `WeirdkeyWorld` starts in a cartless installation state, discovers cartridge manifests, and launches playable cartridges through the same runtime machinery.
- `ProgressionCartridge` begins with follow-the-light, then opens into a branching keyboard-space experiment.

## Run Weirdkey

```bash
./gradlew test
./gradlew :app:run --args='F1 PAUSE F2'
```

Bare arguments are treated as key presses; use `PRESS:KEY`, `HOLD:KEY`, or `RELEASE:KEY` to simulate other input events. The cartless state currently exposes `Progression` as a launchable cartridge and `Paint With Kevin` as an inspectable unavailable cartridge.

Progression starts on `F1`, reverses after `F3`, and then uses bright keys for the moving light and faint keys for reachable neighbors. Hold `SPACE` to reveal the overlapping numpad layer; movement through `W`/`A`/`S`/`D` also moves its other light. `PAUSE` returns early, while reaching and pressing `ESC` completes the cartridge and returns to Weirdkey.

## Run on a G915 X on Windows

Install Logitech G HUB, connect the keyboard, and run:

```powershell
./gradlew :app:run --args="--hardware"
```

Weirdkey saves the current lighting, turns the keyboard dark, and lights one key green. Captured gameplay keys stay inside Weirdkey instead of reaching the foreground Windows app, while Esc remains available to quit and restore the saved lighting. Set `WEIRDKEY_LOGITECH_LED_DLL` only if G HUB's LED SDK DLL is installed outside its standard location.

## Runtime events

Cartridges implement one installation method and compose the events and phases they need. Executable state remains ordinary fields on the cartridge:

```java
public void install(CartridgeContext context) {
	context.on(KeyInputEvent.class, this::handleInput);
	context.postUpdate(debugger::inspect);
}
```

`CartridgeContext` can publish and subscribe without adding device-specific methods to `Cartridge`. Events carry their emitter and tags, subscriptions can expire after a fixed number of deliveries, and completed emissions can chain immediate or delayed follow-ups:

```java
EventTag gameplay = new EventTag("gameplay");

context.onTimes(KeyInputEvent.class, 3, event -> System.out.println(event.keyId()));

context.emit(new InvalidAction("Expected F4"), gameplay)
	.thenEmitAfter(Duration.ofMillis(150), new FeedbackFinished());
```

Dispatch is ordered and fail-fast. Delayed events are serialized with immediate events, and closing the cartridge runtime cancels pending delayed emissions.

## Runtime lifecycle

Every top-level event runs through a deterministic cycle:

```text
PRE_UPDATE -> UPDATE -> POST_UPDATE -> COMMIT -> apply device output
```

Event subscribers run during UPDATE. Cartridge state remains ordinary Java state owned by the cartridge. Phase callbacks can observe it at explicit boundaries:

```java
context.preUpdate(validation::check);
context.update(movement::update);
context.postUpdate(console::inspect);
```

Calls such as `lightKey`, `clearKey`, and `showStatus` are buffered until all COMMIT callbacks finish, then applied while the runtime remains in COMMIT. POST_UPDATE therefore sees completed logical state before physical or display output changes. Reentrant events remain in the current UPDATE; delayed events begin a fresh cycle. A callback failure aborts later phases and discards buffered output for that cycle. Device application is best-effort: once physical I/O begins, a later device failure cannot roll back earlier commands.

New events may be emitted during UPDATE or between cycles. PRE_UPDATE, POST_UPDATE, and COMMIT callbacks are observation boundaries and cannot start another event chain.
