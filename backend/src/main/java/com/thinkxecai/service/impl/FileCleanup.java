package com.thinkxecai.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ties file deletions to the outcome of the current transaction, which files cannot join (ADR 026).
 * A failed deletion is logged and never reaches the caller: the database change already stands, and
 * an exception thrown from afterCommit would otherwise be rethrown to the service's caller.
 */
@Slf4j
final class FileCleanup {

    private FileCleanup() {
    }

    /** Runs {@code deletion} once the current transaction has committed; a rollback keeps the files. */
    static void afterCommit(String what, Runnable deletion) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runLogged(what, deletion);
            }
        });
    }

    /** Runs {@code deletion} if the current transaction ends without committing. */
    static void unlessCommitted(String what, Runnable deletion) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    runLogged(what, deletion);
                }
            }
        });
    }

    private static void runLogged(String what, Runnable deletion) {
        try {
            deletion.run();
        } catch (RuntimeException e) {
            log.warn("Could not delete files of {}; they are left on disk", what, e);
        }
    }

}
