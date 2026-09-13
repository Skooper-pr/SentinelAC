package com.skooper.sentinelac.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageManagerTest {

    @TempDir
    Path tempDir;

    private File dbFile;
    private StorageManager storage;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("test_sentinelac.db").toFile();
        storage = new StorageManager(dbFile, null);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    @DisplayName("Storage: Flags and verdicts persist across storage restarts")
    void testFlagsAndVerdictsPersistenceAcrossRestart() {
        UUID playerId = UUID.randomUUID();
        String playerName = "Cheater123";
        String category = "COMBAT";
        double lambda = 6.95;
        String summary = "Impossible hit: ray misses target AABB";

        // 1. Insert Flag
        int flagId = storage.saveFlag(playerId, playerName, category, lambda, summary);
        assertTrue(flagId > 0, "Flag ID must be positive generated key: " + flagId);

        // 2. Query Flag
        Optional<FlagRecord> flagOpt = storage.getFlag(flagId);
        assertTrue(flagOpt.isPresent());
        FlagRecord flag = flagOpt.get();
        assertEquals(playerId, flag.getPlayerUuid());
        assertEquals(playerName, flag.getPlayerName());
        assertEquals(category, flag.getCategory());
        assertEquals(lambda, flag.getLambdaValue(), 1.0E-4);
        assertEquals(summary, flag.getEvidenceSummary());

        // 3. Insert Verdict
        boolean verdictSaved = storage.saveVerdict(flagId, "upheld", "AdminSteve");
        assertTrue(verdictSaved);

        List<VerdictRecord> verdicts = storage.getVerdictsForFlag(flagId);
        assertEquals(1, verdicts.size());
        assertEquals("upheld", verdicts.get(0).getOutcome());
        assertEquals("AdminSteve", verdicts.get(0).getStaffMember());

        // 4. Simulate Server Restart: Close connection and re-open SQLite file
        storage.close();
        StorageManager restartedStorage = new StorageManager(dbFile, null);

        // Assert flags and verdicts persist across restart
        Optional<FlagRecord> restoredFlag = restartedStorage.getFlag(flagId);
        assertTrue(restoredFlag.isPresent(), "Flag must persist across database re-initialization");
        assertEquals(playerName, restoredFlag.get().getPlayerName());

        List<VerdictRecord> restoredVerdicts = restartedStorage.getVerdictsForFlag(flagId);
        assertEquals(1, restoredVerdicts.size(), "Verdict must persist across database re-initialization");
        assertEquals("upheld", restoredVerdicts.get(0).getOutcome());
        assertEquals("AdminSteve", restoredVerdicts.get(0).getStaffMember());

        restartedStorage.close();
    }
}
