package weirdkey.runtime.events;

import java.util.Optional;
import java.util.Set;

public record EventEnvelope<E>(E event, Object emitter, Set<EventTag> tags) {
    public EventEnvelope {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (emitter == null) {
            throw new IllegalArgumentException("emitter must not be null");
        }
        tags = Set.copyOf(tags);
    }

    public boolean hasTag(EventTag tag) {
        return tags.contains(tag);
    }

    public <T> Optional<T> emitterAs(Class<T> type) {
        return type.isInstance(emitter) ? Optional.of(type.cast(emitter)) : Optional.empty();
    }
}