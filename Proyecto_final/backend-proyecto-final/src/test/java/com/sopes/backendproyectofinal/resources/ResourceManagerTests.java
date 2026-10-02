package com.sopes.backendproyectofinal.resources;

import com.sopes.backendproyectofinal.shared.SimulationState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class ResourceManagerTests {
    @Test
    void rollsBackPartialPermitsWhileWaitingAndReleasesExactlyOnce() throws Exception {
        ResourceManager manager = new ResourceManager(new SimulationState());
        List<ResourceManager.Lease> leases = new ArrayList<>();
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            for (int index = 0; index < 6; index++) {
                leases.add(manager.acquire("order-" + index, Map.of(ResourceType.WORKER, 1, ResourceType.PACKING, 1)));
            }
            var blocked = executor.submit(() -> {
                try (var lease = manager.acquire("waiting", Map.of(ResourceType.WORKER, 1, ResourceType.PACKING, 1))) {
                    fail("El séptimo pedido no debe adquirir empaque.");
                } catch (InterruptedException exception) { interrupted.countDown(); }
            });
            await().atMost(2, TimeUnit.SECONDS).until(() -> manager.pendingRequests().size() == 1);
            assertEquals(6, manager.snapshot().stream().filter(resource -> resource.type() == ResourceType.WORKER).findFirst().orElseThrow().inUse());
            assertEquals(6, manager.snapshot().stream().filter(resource -> resource.type() == ResourceType.PACKING).findFirst().orElseThrow().inUse());
            blocked.cancel(true);
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertTrue(manager.pendingRequests().isEmpty());
        } finally {
            leases.forEach(ResourceManager.Lease::close);
            leases.forEach(ResourceManager.Lease::close);
            executor.shutdownNow();
        }
        assertTrue(manager.snapshot().stream().allMatch(resource -> resource.inUse() == 0
                && resource.instances().stream().allMatch(instance -> instance.ownerId() == null)));
    }

    @Test
    void wakesBlockedOperationAfterOwnerReleasesScanner() throws Exception {
        ResourceManager manager = new ResourceManager(new SimulationState());
        var executor = Executors.newSingleThreadExecutor();
        var first = manager.acquire("receipt", Map.of(ResourceType.SCANNER, 1));
        try {
            var second = executor.submit(() -> {
                try (var lease = manager.acquire("dispatch", Map.of(ResourceType.SCANNER, 1))) {
                    return manager.snapshot().stream().filter(resource -> resource.type() == ResourceType.SCANNER)
                            .findFirst().orElseThrow().instances().get(0).ownerId();
                }
            });
            await().atMost(2, TimeUnit.SECONDS).until(() -> !manager.pendingRequests().isEmpty());
            assertFalse(second.isDone());
            first.close();
            assertEquals("dispatch", second.get(2, TimeUnit.SECONDS));
        } finally { first.close(); executor.shutdownNow(); }
        assertTrue(manager.snapshot().stream().allMatch(resource -> resource.inUse() == 0));
    }

    @Test
    void rejectsImpossibleResourceRequestWithoutWaiting() {
        ResourceManager manager = new ResourceManager(new SimulationState());
        assertThrows(IllegalArgumentException.class, () -> manager.acquire("invalid", Map.of(ResourceType.SCANNER, 2)));
        assertTrue(manager.pendingRequests().isEmpty());
    }
}
