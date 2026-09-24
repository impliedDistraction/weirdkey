package weirdkey.runtime.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EventBusTest {
    private static final EventTag GAMEPLAY = new EventTag("gameplay");
    private static final EventTag FEEDBACK = new EventTag("feedback");

    @Test
    void dispatchesByTypeAndTagsWithEmitterContext() {
        Object emitter = new Object();
        List<EventEnvelope<Message>> seen = new ArrayList<>();

        try (EventBus events = new EventBus()) {
            events.subscribe(Message.class, Set.of(GAMEPLAY), seen::add);

            events.emit(new Message("ignored"), emitter, FEEDBACK);
            events.emit(new Message("accepted"), emitter, GAMEPLAY, FEEDBACK);
        }

        assertEquals(1, seen.size());
        assertEquals("accepted", seen.get(0).event().text());
        assertTrue(seen.get(0).hasTag(FEEDBACK));
        assertSame(emitter, seen.get(0).emitterAs(Object.class).orElseThrow());
        assertTrue(seen.get(0).emitterAs(String.class).isEmpty());
    }

    @Test
    void finiteSubscriptionsCleanThemselvesUp() {
        List<String> seen = new ArrayList<>();

        try (EventBus events = new EventBus()) {
            EventSubscription subscription = events.subscribeTimes(Message.class, 2, envelope -> {
                seen.add(envelope.event().text());
            });

            events.emit(new Message("one"), this);
            events.emit(new Message("two"), this);
            events.emit(new Message("three"), this);

            assertFalse(subscription.isActive());
        }

        assertEquals(List.of("one", "two"), seen);
    }

    @Test
    void chainsAfterCurrentListenersFinish() throws Exception {
        List<String> order = new ArrayList<>();

        try (EventBus events = new EventBus()) {
            events.subscribe(Message.class, envelope -> order.add(envelope.event().text()));

            EventEmission<Message> first = events.emit(new Message("x"), this, GAMEPLAY);
            first.thenEmit(new Message("y"));
            first.thenEmitAfter(Duration.ofMillis(10), new Message("z")).get(1, TimeUnit.SECONDS);
        }

        assertEquals(List.of("x", "y", "z"), order);
    }

    @Test
    void subscriptionsCanBeCancelledExplicitly() {
        List<Message> seen = new ArrayList<>();

        try (EventBus events = new EventBus()) {
            EventSubscription subscription = events.subscribe(Message.class, envelope -> seen.add(envelope.event()));
            subscription.cancel();
            events.emit(new Message("ignored"), this);

            assertFalse(subscription.isActive());
        }

        assertTrue(seen.isEmpty());
    }

    @Test
    void closingCancelsPendingDelayedEmissions() {
        EventBus events = new EventBus();
        CompletableFuture<EventEmission<Message>> pending = events.emitAfter(
            Duration.ofMinutes(1),
            new Message("too late"),
            this,
            Set.of()
        );

        events.close();

        assertTrue(pending.isCompletedExceptionally());
        assertThrows(CancellationException.class, pending::join);
    }

    @Test
    void listenerFailuresStopDispatchAndPropagate() {
        List<String> seen = new ArrayList<>();

        try (EventBus events = new EventBus()) {
            events.subscribe(Message.class, envelope -> {
                throw new IllegalStateException("failed update");
            });
            events.subscribe(Message.class, envelope -> seen.add(envelope.event().text()));

            IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> events.emit(new Message("partial"), this)
            );
            assertEquals("failed update", failure.getMessage());
        }

        assertTrue(seen.isEmpty());
    }

    private record Message(String text) {
    }
}