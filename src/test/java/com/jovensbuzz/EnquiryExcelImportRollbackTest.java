package com.jovensbuzz;

import com.jovensbuzz.enquiry.entity.EnquiryStatusHistory;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.enquiry.repository.EnquiryStatusHistoryRepository;
import com.jovensbuzz.enquiry.service.EnquiryExcelImportService;
import com.jovensbuzz.enquiry.service.EnquiryExcelImportService.RowInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * A database failure in the middle of an import must roll back every row already written.
 */
@SpringBootTest
class EnquiryExcelImportRollbackTest {

    @Autowired private EnquiryExcelImportService importService;
    @Autowired private EnquiryRepository enquiryRepository;
    @SpyBean private EnquiryStatusHistoryRepository statusHistoryRepository;

    @Test
    void failureAfterFirstRowIsWritten_rollsBackEverything() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        // The first enquiry row is INSERTed immediately (IDENTITY key); the failure comes right after it
        doThrow(new IllegalStateException("simulated database failure"))
                .when(statusHistoryRepository).save(any(EnquiryStatusHistory.class));

        long before = enquiryRepository.count();
        List<RowInput> rows = List.of(
                new RowInput(2, "Roll", "One", "one-" + tag + "@example.com", "US", "(201) 555-0123", "", "Other", "first", "", ""),
                new RowInput(3, "Roll", "Two", "two-" + tag + "@example.com", "US", "(201) 555-0124", "", "Other", "second", "", ""));
        assertThrows(RuntimeException.class, () -> // Spring translates it to a DataAccessException
                importService.commit(new EnquiryExcelImportService.CommitRequest(rows, false, "rollback.xlsx", 0), "test_admin"));

        assertEquals(before, enquiryRepository.count(), "the first row was rolled back as well");
        assertTrue(enquiryRepository.findAll().stream().noneMatch(e -> e.getEmail().contains(tag)));
    }
}
