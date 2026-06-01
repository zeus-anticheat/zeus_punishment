package org.vennv.zeuspunishment.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZeusApiClientStatusTest {
    @Test
    void snapshotCopiesCachedStateWithoutNetwork() {
        ZeusApiClient client = new ZeusApiClient("http://127.0.0.1:1");
        ApiStatusSnapshot snapshot = client.getStatusSnapshot();
        assertFalse(snapshot.isStreamRunning());
        assertFalse(snapshot.isReconnecting());
        assertEquals(0, snapshot.getGeneration());
        assertEquals("none", snapshot.getLastError());
    }
}
