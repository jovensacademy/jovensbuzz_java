package com.jovensbuzz;

import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.enquiry.dto.EnquiryNoteRequest;
import com.jovensbuzz.enquiry.dto.EnquiryStatusUpdateRequest;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.enquiry.service.EnquiryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent writes to one enquiry (several admins, double clicks) used to deadlock in MariaDB: each
 * transaction took a shared lock on the enquiry row through the history/note foreign key and then waited
 * for the exclusive lock to update it. Writers now lock the row first, so they queue and all succeed.
 */
@SpringBootTest
class EnquiryConcurrencyIntegrationTest {

    private static final String[] STATUSES = {"CONTACTED", "QUALIFIED", "CONVERTED", "CLOSED", "NEW", "CONTACTED", "QUALIFIED", "CLOSED"};

    @Autowired private EnquiryService enquiryService;
    @Autowired private EnquiryRepository enquiryRepository;
    @Autowired private JdbcTemplate jdbc;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Long id;

    @BeforeEach
    void setUp() {
        Enquiry e = new Enquiry();
        e.setPublicReference("JB-C-" + tag.toUpperCase(Locale.ROOT));
        e.setFullName("Concurrency " + tag);
        e.setEmail("concurrency-" + tag + "@example.com");
        e.setCompanyName("CONC-" + tag);
        e.setServiceInterest("Other");
        e.setMessage("Concurrency test");
        e.setStatus(EnquiryStatus.NEW);
        e.setSource("WEBSITE_CONTACT");
        e.setCreatedAt(Instant.now());
        id = enquiryRepository.save(e).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM enquiries WHERE company_name = ?", "CONC-" + tag); // test database only
    }

    @Test
    void concurrentStatusChangesAndNotes_allSucceed_withoutDeadlocks() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(STATUSES.length * 2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (String status : STATUSES) {
            results.add(pool.submit(() -> {
                start.await();
                return enquiryService.updateStatus(id, new EnquiryStatusUpdateRequest(status, "concurrent " + status), "test_admin");
            }));
            results.add(pool.submit(() -> {
                start.await();
                return enquiryService.addNote(id, new EnquiryNoteRequest("parallel note " + status), "test_admin");
            }));
        }
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> f : results) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                // Business-rule refusals (e.g. "already CLOSED", disallowed transition) are correct answers;
                // anything else (deadlock, lock timeout, 500) is a failure
                if (!(ex.getCause() instanceof BadRequestException)) failures.add(ex.getCause());
            }
        }
        pool.shutdown();
        assertTrue(failures.isEmpty(), () -> "concurrent writes failed: " + failures);

        // Changes were serialised: each history row starts from the previous row's status (no lost update)
        // and the enquiry ends in the last recorded status
        List<String[]> history = jdbc.query("SELECT previous_status, new_status FROM enquiry_status_history WHERE enquiry_id = ? ORDER BY id",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, id);
        assertFalse(history.isEmpty(), "at least one status change applied");
        String current = "NEW";
        for (String[] row : history) {
            assertEquals(current, row[0], "history chain");
            current = row[1];
        }
        assertEquals(current, jdbc.queryForObject("SELECT status FROM enquiries WHERE id = ?", String.class, id));
        Integer notes = jdbc.queryForObject("SELECT COUNT(*) FROM enquiry_notes WHERE enquiry_id = ? AND note LIKE 'parallel note%'", Integer.class, id);
        assertEquals(STATUSES.length, notes);
    }
}
