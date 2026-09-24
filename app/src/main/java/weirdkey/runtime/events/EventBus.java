package weirdkey.runtime.events;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class EventBus implements AutoCloseable {
    private final List<Subscriber<?>> subscribers = new ArrayList<>();
    private final List<CompletableFuture<?>> pendingEmissions = new ArrayList<>();
    private final Object dispatchLock = new Object();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "weirdkey-events");
        thread.setDaemon(true);
        return thread;
    });
    private boolean closed;

    public <E> EventSubscription subscribe(Class<E> eventType, Consumer<EventEnvelope<E>> listener) {
        return subscribe(eventType, Set.of(), Integer.MAX_VALUE, listener);
    }

    public <E> EventSubscription subscribe(
        Class<E> eventType,
        Set<EventTag> requiredTags,
        Consumer<EventEnvelope<E>> listener
    ) {
        return subscribe(eventType, requiredTags, Integer.MAX_VALUE, listener);
    }

    public <E> EventSubscription subscribeTimes(
        Class<E> eventType,
        int deliveryLimit,
        Consumer<EventEnvelope<E>> listener
    ) {
        return subscribe(eventType, Set.of(), deliveryLimit, listener);
    }

    public <E> EventSubscription subscribe(
        Class<E> eventType,
        Set<EventTag> requiredTags,
        int deliveryLimit,
        Consumer<EventEnvelope<E>> listener
    ) {
        if (deliveryLimit < 1) {
            throw new IllegalArgumentException("deliveryLimit must be at least 1");
        }

        Subscriber<E> subscriber = new Subscriber<>(eventType, requiredTags, deliveryLimit, listener);
        synchronized (subscribers) {
            requireOpen();
            subscribers.add(subscriber);
        }
        return subscriber;
    }

    public <E> EventEmission<E> emit(E event, Object emitter, EventTag... tags) {
        return emit(event, emitter, Set.copyOf(Arrays.asList(tags)));
    }

    public <E> EventEmission<E> emit(E event, Object emitter, Set<EventTag> tags) {
        EventEnvelope<E> envelope = new EventEnvelope<>(event, emitter, tags);
        synchronized (dispatchLock) {
            List<Subscriber<?>> snapshot;
            synchronized (subscribers) {
                requireOpen();
                snapshot = List.copyOf(subscribers);
            }

            try {
                for (Subscriber<?> subscriber : snapshot) {
                    subscriber.deliver(envelope);
                }
            } finally {
                synchronized (subscribers) {
                    subscribers.removeIf(subscriber -> !subscriber.isActive());
                }
            }
            return new EventEmission<>(this, envelope);
        }
    }

    public <E> CompletableFuture<EventEmission<E>> emitAfter(
        Duration delay,
        E event,
        Object emitter,
        Set<EventTag> tags
    ) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }

        CompletableFuture<EventEmission<E>> result = new CompletableFuture<>();
        synchronized (subscribers) {
            requireOpen();
            pendingEmissions.add(result);
            scheduler.schedule(() -> {
                try {
                    result.complete(emit(event, emitter, tags));
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
        }
        result.whenComplete((emission, failure) -> {
            synchronized (subscribers) {
                pendingEmissions.remove(result);
            }
        });
        return result;
    }

    @Override
    public void close() {
        synchronized (subscribers) {
            if (closed) {
                return;
            }
            closed = true;
            subscribers.forEach(Subscriber::cancel);
            subscribers.clear();
            List<CompletableFuture<?>> cancelledEmissions = List.copyOf(pendingEmissions);
            pendingEmissions.clear();
            cancelledEmissions.forEach(future -> future.completeExceptionally(
                new CancellationException("Event bus closed before delayed emission")
            ));
        }
        scheduler.shutdownNow();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Event bus is closed");
        }
    }

    private static final class Subscriber<E> implements EventSubscription {
        private final Class<E> eventType;
        private final Set<EventTag> requiredTags;
        private final Consumer<EventEnvelope<E>> listener;
        private int remainingDeliveries;
        private boolean active = true;

        private Subscriber(
            Class<E> eventType,
            Set<EventTag> requiredTags,
            int deliveryLimit,
            Consumer<EventEnvelope<E>> listener
        ) {
            this.eventType = eventType;
            this.requiredTags = Set.copyOf(requiredTags);
            this.remainingDeliveries = deliveryLimit;
            this.listener = listener;
        }

        private void deliver(EventEnvelope<?> envelope) {
            if (!eventType.isInstance(envelope.event()) || !envelope.tags().containsAll(requiredTags) || !claim()) {
                return;
            }
            listener.accept(new EventEnvelope<>(eventType.cast(envelope.event()), envelope.emitter(), envelope.tags()));
        }

        private synchronized boolean claim() {
            if (!active) {
                return false;
            }
            remainingDeliveries--;
            if (remainingDeliveries == 0) {
                active = false;
            }
            return true;
        }

        @Override
        public synchronized boolean isActive() {
            return active;
        }

        @Override
        public synchronized void cancel() {
            active = false;
        }
    }
}