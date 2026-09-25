package weirdkey.runtime;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import weirdkey.runtime.events.EventEmission;
import weirdkey.runtime.events.EventEnvelope;
import weirdkey.runtime.events.EventSubscription;
import weirdkey.runtime.events.EventTag;

public final class InstallationContext {
    private final GameContext game;
    private final Object installationState;

    InstallationContext(GameContext game, Object installationState) {
        this.game = game;
        this.installationState = installationState;
    }

    public KeyboardTopology topology() {
        return game.topology();
    }

    public <E> EventSubscription on(Class<E> eventType, Consumer<E> listener) {
        return game.events().subscribe(eventType, envelope -> listener.accept(envelope.event()));
    }

    public <E> EventSubscription on(
        Class<E> eventType,
        Set<EventTag> requiredTags,
        Consumer<E> listener
    ) {
        return game.events().subscribe(eventType, requiredTags, envelope -> listener.accept(envelope.event()));
    }

    public <E> EventSubscription onTimes(Class<E> eventType, int deliveryLimit, Consumer<E> listener) {
        return game.events().subscribeTimes(
            eventType,
            deliveryLimit,
            envelope -> listener.accept(envelope.event())
        );
    }

    public <E> EventSubscription onEnvelope(
        Class<E> eventType,
        Consumer<EventEnvelope<E>> listener
    ) {
        return game.events().subscribe(eventType, listener);
    }

    public <E> EventSubscription onEnvelope(
        Class<E> eventType,
        Set<EventTag> requiredTags,
        Consumer<EventEnvelope<E>> listener
    ) {
        return game.events().subscribe(eventType, requiredTags, listener);
    }

    public <E> EventEmission<E> emit(E event, EventTag... tags) {
        return game.events().emit(event, installationState, tags);
    }

    public <E> CompletableFuture<EventEmission<E>> emitAfter(Duration delay, E event, EventTag... tags) {
        return game.events().emitAfter(
            delay,
            event,
            installationState,
            Set.copyOf(Arrays.asList(tags))
        );
    }

    public void preUpdate(Runnable callback) {
        game.onPhase(LifecyclePhase.PRE_UPDATE, callback);
    }

    public void update(Runnable callback) {
        game.onPhase(LifecyclePhase.UPDATE, callback);
    }

    public void postUpdate(Runnable callback) {
        game.onPhase(LifecyclePhase.POST_UPDATE, callback);
    }

    public void commit(Runnable callback) {
        game.onPhase(LifecyclePhase.COMMIT, callback);
    }

    public Optional<LifecyclePhase> currentPhase() {
        return game.currentPhase();
    }

    public void lightKey(String keyId, KeyColor color) {
        game.lightKey(keyId, color);
    }

    public void clearKey(String keyId) {
        game.clearKey(keyId);
    }

    public void showStatus(String message) {
        game.showStatus(message);
    }

    public void captureInputKeys(Collection<String> keyIds) {
        game.captureInputKeys(keyIds);
    }
}
