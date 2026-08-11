package com.example.ilgeobolkka.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PerformanceDatasetPlanTest {

    private final PerformanceDatasetPlan datasetPlan = new PerformanceDatasetPlan();

    @Test
    void mvp_행_수와_상태_분포가_고정된다() {
        PerformanceDatasetPlan.Dataset dataset = datasetPlan.create("{noop}performance");

        assertEquals(100, dataset.books().size());
        assertEquals(400, dataset.pages().size());
        assertEquals(1_000, dataset.readers().size());
        assertEquals(1_000, dataset.inkAccounts().size());
        assertEquals(1_000, dataset.inkPurchases().size());
        assertEquals(1_333, dataset.inkLedgers().size());
        assertEquals(333, dataset.pageRentals().size());
        assertEquals(333, dataset.ownershipPayments().size());
        assertEquals(333, dataset.bookOwnerships().size());
        assertEquals(666, dataset.libraryEntries().size());
    }

    @Test
    void 같은_입력은_같은_데이터를_만든다() {
        PerformanceDatasetPlan.Dataset first = datasetPlan.create("{noop}performance");
        PerformanceDatasetPlan.Dataset second = datasetPlan.create("{noop}performance");

        assertEquals(first, second);
        assertEquals(
                first.readers().size(),
                new HashSet<>(first.readers().stream()
                                .map(PerformanceDatasetPlan.ReaderSeed::email)
                                .toList())
                        .size());
    }
}
