package com.thinkxecai.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Which transaction outcomes delete files (ADR 026). */
class FileCleanupTest {

    private final AtomicInteger deletions = new AtomicInteger();

    @BeforeEach
    void startSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void anUploadThatRolledBackDeletesItsFiles() {
        FileCleanup.unlessCommitted("upload", deletions::incrementAndGet);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(deletions).hasValue(1);
    }

    @Test
    void anUploadThatCommittedOrMayHaveCommittedKeepsItsFiles() {
        // STATUS_UNKNOWN: the commit may have gone through, and then its chunks point at these files
        FileCleanup.unlessCommitted("upload", deletions::incrementAndGet);

        complete(TransactionSynchronization.STATUS_COMMITTED);
        complete(TransactionSynchronization.STATUS_UNKNOWN);

        assertThat(deletions).hasValue(0);
    }

    private static void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }

}
