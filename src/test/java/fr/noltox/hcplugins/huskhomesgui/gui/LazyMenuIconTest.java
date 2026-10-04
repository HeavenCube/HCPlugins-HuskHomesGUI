package fr.noltox.hcplugins.huskhomesgui.gui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LazyMenuIconTest {

    @Test
    void unseenPagesAreNotRenderedAndVisitedIconIsReused() {
        var renders = new AtomicInteger();
        Object result = new Object();
        var icon = new LazyMenuIcon<>(() -> {
            renders.incrementAndGet();
            return result;
        });
        assertEquals(0, renders.get());
        assertSame(result, icon.apply(null));
        assertSame(result, icon.apply(null));
        assertEquals(1, renders.get());
    }

    @Test
    void reopeningCreatesAFreshRenderAndFailedRenderCanBeRetried() {
        var renders = new AtomicInteger();
        var icon = new LazyMenuIcon<>(() -> {
            if (renders.incrementAndGet() == 1) {
                throw new IllegalStateException("render failed");
            }
            return new Object();
        });
        assertThrows(IllegalStateException.class, () -> icon.apply(null));
        assertNotNull(icon.apply(null));
        var reopened = new LazyMenuIcon<>(Object::new);
        assertNotSame(icon.apply(null), reopened.apply(null));
    }

}
