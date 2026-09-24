package weirdkey.runtime.events;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class EventEmission<E> {
    private final EventBus eventBus;
    private final EventEnvelope<E> envelope;

    EventEmission(EventBus eventBus, EventEnvelope<E> envelope) {
        this.eventBus = eventBus;
        this.envelope = envelope;
    }

    public EventEnvelope<E> envelope() {
        return envelope;
    }

    public <N> EventEmission<N> thenEmit(N event) {
        return eventBus.emit(event, envelope.emitter(), envelope.tags());
    }

    public <N> CompletableFuture<EventEmission<N>> thenEmitAfter(Duration delay, N event) {
        return eventBus.emitAfter(delay, event, envelope.emitter(), envelope.tags());
    }
}