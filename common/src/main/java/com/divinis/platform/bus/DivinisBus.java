package com.divinis.platform.bus;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.divinis.Divinis;
import com.divinis.platform.Dist;

/**
 * Divinis' own event bus. The loaders' events (NeoForge's, Fabric's callbacks, the mixins) are turned into Divinis'
 * events (platform.event) and posted here, so the game code listens to one set of events on every loader.
 * A listener is a static method annotated with @SubscribeEvent in a class annotated with @EventBusSubscriber, or a
 * Consumer added with addListener. A listener of an event class also hears its subclasses.
 */
public final class DivinisBus {
    private record Listener(Class<?> type, EventPriority priority, boolean receiveCanceled, Consumer<Object> call) {}

    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Map<Class<?>, Listener[]> BY_TYPE = new ConcurrentHashMap<>();

    private DivinisBus() {}

    /** Registers the static @SubscribeEvent methods of these classes, those meant for this side. */
    public static void register(Dist side, Class<?>... classes) {
        for (Class<?> c : classes) {
            EventBusSubscriber sub = c.getAnnotation(EventBusSubscriber.class);
            if (sub != null && !List.of(sub.value()).contains(side)) continue;
            for (Method m : c.getDeclaredMethods()) {
                SubscribeEvent ann = m.getAnnotation(SubscribeEvent.class);
                if (ann == null) continue;
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) {
                    throw new IllegalStateException("Listener " + c.getName() + "." + m.getName() + " must be static with one parameter");
                }
                try {
                    m.setAccessible(true);
                    MethodHandle h = MethodHandles.lookup().unreflect(m);
                    add(new Listener(m.getParameterTypes()[0], ann.priority(), ann.receiveCanceled(), e -> {
                        try {
                            h.invoke(e);
                        } catch (RuntimeException | Error ex) {
                            throw ex;
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    }));
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <E> void addListener(Class<E> type, Consumer<E> listener) {
        add(new Listener(type, EventPriority.NORMAL, false, (Consumer<Object>) listener));
    }

    private static synchronized void add(Listener l) {
        LISTENERS.add(l);
        BY_TYPE.clear();
    }

    /** Whether anything listens to this event class (the loaders skip building events nobody hears). */
    public static boolean hasListeners(Class<?> type) {
        return listeners(type).length > 0;
    }

    private static Listener[] listeners(Class<?> type) {
        return BY_TYPE.computeIfAbsent(type, t -> {
            synchronized (DivinisBus.class) {
                return LISTENERS.stream().filter(l -> l.type.isAssignableFrom(t))
                        .sorted(Comparator.comparing(Listener::priority)).toArray(Listener[]::new);
            }
        });
    }

    /** Calls every listener of the event (a listener that throws is logged, the others still run); returns it. */
    public static <E> E post(E event) {
        for (Listener l : listeners(event.getClass())) {
            if (event instanceof ICancellableEvent c && c.isCanceled() && !l.receiveCanceled) continue;
            try {
                l.call.accept(event);
            } catch (RuntimeException ex) {
                Divinis.LOGGER.error("A listener of {} failed", event.getClass().getSimpleName(), ex);
            }
        }
        return event;
    }
}
