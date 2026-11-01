package com.jovensbuzz.dashboard.service;

import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.dashboard.dto.DashboardSummaryDto;
import com.jovensbuzz.dashboard.dto.EnquiryTrendDto;
import com.jovensbuzz.dashboard.dto.ServiceDistributionDto;
import com.jovensbuzz.dashboard.dto.StatusDistributionDto;
import com.jovensbuzz.enquiry.dto.EnquiryResponseDto;
import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import com.jovensbuzz.enquiry.repository.EnquiryRepository;
import com.jovensbuzz.enquiry.service.EnquiryService;
import com.jovensbuzz.integration.sync.entity.SyncStatus;
import com.jovensbuzz.integration.sync.repository.IntegrationSyncJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard metrics computed live from MySQL. Only metrics backed by stored data are
 * reported; nothing is estimated or fabricated.
 */
@Service
public class DashboardService {

    private static final int MAX_RANGE_DAYS = 366;

    private final EnquiryRepository enquiryRepository;
    private final IntegrationSyncJobRepository syncJobRepository;
    private final EnquiryService enquiryService;

    public DashboardService(
            EnquiryRepository enquiryRepository,
            IntegrationSyncJobRepository syncJobRepository,
            EnquiryService enquiryService) {
        this.enquiryRepository = enquiryRepository;
        this.syncJobRepository = syncJobRepository;
        this.enquiryService = enquiryService;
    }

    public static ZoneId parseZone(String tz) {
        if (tz == null || tz.isBlank()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + tz);
        }
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary(ZoneId zone) {
        DashboardSummaryDto summary = new DashboardSummaryDto();

        Map<EnquiryStatus, Long> byStatus = statusCounts(null, null);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long converted = byStatus.getOrDefault(EnquiryStatus.CONVERTED, 0L);

        LocalDate today = LocalDate.now(zone);
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();

        List<Enquiry> recent = enquiryRepository.findRecentEnquiries(PageRequest.of(0, 6));
        Map<Long, String> syncStatuses = enquiryService.latestSyncStatuses(recent);

        summary.setTotalEnquiries(total);
        summary.setNewEnquiries(byStatus.getOrDefault(EnquiryStatus.NEW, 0L));
        summary.setContactedEnquiries(byStatus.getOrDefault(EnquiryStatus.CONTACTED, 0L));
        summary.setQualifiedLeads(byStatus.getOrDefault(EnquiryStatus.QUALIFIED, 0L));
        summary.setConvertedLeads(converted);
        summary.setClosedEnquiries(byStatus.getOrDefault(EnquiryStatus.CLOSED, 0L));
        summary.setSpamEnquiries(byStatus.getOrDefault(EnquiryStatus.SPAM, 0L));
        summary.setEnquiriesToday(enquiryRepository.countByCreatedAtAfter(startOfToday));
        summary.setEnquiriesThisMonth(enquiryRepository.countByCreatedAtAfter(startOfMonth));
        summary.setConversionRate(total > 0 ? Math.round(((double) converted / total) * 1000.0) / 10.0 : 0.0);
        summary.setSyncFailed(syncJobRepository.countByStatus(SyncStatus.FAILED));
        summary.setSyncPending(syncJobRepository.countByStatus(SyncStatus.PENDING)
                + syncJobRepository.countByStatus(SyncStatus.RETRYING)
                + syncJobRepository.countByStatus(SyncStatus.PROCESSING));
        summary.setRecentEnquiries(recent.stream()
                .map(e -> EnquiryResponseDto.fromEntity(e, syncStatuses.getOrDefault(e.getId(), "NOT_QUEUED")))
                .collect(Collectors.toList()));
        return summary;
    }

    /**
     * Daily enquiry counts for an inclusive date range in the given zone. Days without
     * enquiries are returned with a zero count so charts have a continuous axis.
     */
    @Transactional(readOnly = true)
    public List<EnquiryTrendDto> getEnquiryTrends(LocalDate from, LocalDate to, ZoneId zone) {
        DateRange range = resolveRange(from, to, zone, 30);
        Map<LocalDate, Long> counts = enquiryRepository.findCreatedAtBetween(range.start(), range.end()).stream()
                .collect(Collectors.groupingBy(instant -> instant.atZone(zone).toLocalDate(), Collectors.counting()));

        DateTimeFormatter label = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);
        List<EnquiryTrendDto> trends = new ArrayList<>();
        for (LocalDate day = range.fromDate(); !day.isAfter(range.toDate()); day = day.plusDays(1)) {
            EnquiryTrendDto dto = new EnquiryTrendDto(day.format(label), counts.getOrDefault(day, 0L));
            dto.setDate(day.toString());
            trends.add(dto);
        }
        return trends;
    }

    @Transactional(readOnly = true)
    public List<ServiceDistributionDto> getServiceDistribution(LocalDate from, LocalDate to, ZoneId zone) {
        DateRange range = from == null && to == null ? null : resolveRange(from, to, zone, 30);
        List<Object[]> rows = enquiryRepository.countByServiceInterestGrouped(
                range != null ? range.start() : null, range != null ? range.end() : null);
        long total = rows.stream().mapToLong(row -> (Long) row[1]).sum();

        List<ServiceDistributionDto> distribution = new ArrayList<>();
        for (Object[] row : rows) {
            long count = (Long) row[1];
            double percentage = total > 0 ? Math.round(((double) count / total) * 1000.0) / 10.0 : 0.0;
            distribution.add(new ServiceDistributionDto((String) row[0], count, percentage));
        }
        return distribution;
    }

    @Transactional(readOnly = true)
    public List<StatusDistributionDto> getStatusDistribution(LocalDate from, LocalDate to, ZoneId zone) {
        DateRange range = from == null && to == null ? null : resolveRange(from, to, zone, 30);
        Map<EnquiryStatus, Long> counts = statusCounts(
                range != null ? range.start() : null, range != null ? range.end() : null);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<StatusDistributionDto> result = new ArrayList<>();
        for (EnquiryStatus status : EnquiryStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            double percentage = total > 0 ? Math.round(((double) count / total) * 1000.0) / 10.0 : 0.0;
            result.add(new StatusDistributionDto(status.name(), count, percentage));
        }
        return result;
    }

    private Map<EnquiryStatus, Long> statusCounts(Instant from, Instant to) {
        Map<EnquiryStatus, Long> counts = new EnumMap<>(EnquiryStatus.class);
        for (Object[] row : enquiryRepository.countByStatusGrouped(from, to)) {
            counts.put((EnquiryStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    private DateRange resolveRange(LocalDate from, LocalDate to, ZoneId zone, int defaultDays) {
        LocalDate toDate = to != null ? to : LocalDate.now(zone);
        LocalDate fromDate = from != null ? from : toDate.minusDays(defaultDays - 1L);
        if (fromDate.isAfter(toDate)) {
            throw new BadRequestException("'from' must be on or before 'to'");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
        return new DateRange(fromDate, toDate,
                fromDate.atStartOfDay(zone).toInstant(),
                toDate.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate, Instant start, Instant end) {}
}
