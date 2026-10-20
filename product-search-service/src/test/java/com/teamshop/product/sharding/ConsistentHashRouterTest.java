package com.teamshop.product.sharding;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ConsistentHashRouterTest {
    @Test
    void routesSameKeyToSameShard() {
        ConsistentHashRouter router = new ConsistentHashRouter(3, 100);
        assertEquals(router.getShard("P1001"), router.getShard("P1001"));
    }

    @Test
    void addingShardAddsItToRing() {
        ConsistentHashRouter router = new ConsistentHashRouter(3, 100);
        router.addShard("shard4");
        assertEquals(4, router.getShards().size());
        assertTrue(router.getShards().contains("shard4"));
    }

    @Test
    void removingShardRemovesItFromRing() {
        ConsistentHashRouter router = new ConsistentHashRouter(3, 100);
        assertTrue(router.removeShard("shard2"));
        assertFalse(router.getShards().contains("shard2"));
        assertEquals(2, router.getShards().size());
    }

    @Test
    void cannotRemoveLastShard() {
        ConsistentHashRouter router = new ConsistentHashRouter(1, 100);
        assertFalse(router.removeShard("shard1"));
        assertEquals(1, router.getShards().size());
    }

    @Test
    void routesKeysAcrossMultipleShards() {
        ConsistentHashRouter router = new ConsistentHashRouter(3, 100);
        java.util.Set<String> destinations = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) destinations.add(router.getShard("product-" + i));
        assertTrue(destinations.size() > 1);
    }
}
