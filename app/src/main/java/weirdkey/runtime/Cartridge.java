package weirdkey.runtime;

public interface Cartridge {
    void start(GameContext context);

    void onInput(GameContext context, KeyInputEvent event);
}
