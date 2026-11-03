package com.jovensbuzz;

import com.jovensbuzz.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** Database trouble maps to retryable statuses with no internals in the body. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void lockConflict_is409() {
        var response = handler.handleLockConflict(new CannotAcquireLockException("Deadlock found when trying to get lock"));
        assertEquals(409, response.getStatusCode().value());
        assertFalse(response.getBody().getMessage().contains("Deadlock"));
    }

    @Test
    void timeoutsAndOutages_are503() {
        assertEquals(503, handler.handleDatabaseUnavailable(new QueryTimeoutException("Statement cancelled due to timeout")).getStatusCode().value());
        assertEquals(503, handler.handleDatabaseUnavailable(new CannotCreateTransactionException("Could not open JPA EntityManager")).getStatusCode().value());
        // Rollback after a timeout on a connection the pool already closed (observed in the DB-stall E2E)
        var rollback = handler.handleTransactionFailure(new JpaSystemException(
                new RuntimeException("Unable to rollback against JDBC Connection", new SQLException("Connection is closed"))));
        assertEquals(503, rollback.getStatusCode().value());
        assertFalse(rollback.getBody().getMessage().contains("JDBC"));
    }

    @Test
    void otherTransactionFailures_stayGeneric500() {
        assertEquals(500, handler.handleTransactionFailure(new JpaSystemException(new RuntimeException("mapping problem"))).getStatusCode().value());
    }
}
