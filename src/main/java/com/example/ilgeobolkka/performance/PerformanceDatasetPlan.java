package com.example.ilgeobolkka.performance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class PerformanceDatasetPlan {

    static final int BOOK_COUNT = 100;
    static final int PAGES_PER_BOOK = 4;
    static final int READER_COUNT = 1_000;
    static final int ACTIVE_READER_COUNT = 333;
    static final int OWNED_READER_COUNT = 333;
    static final int NEW_READER_COUNT = 334;

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 11, 0, 0);
    private static final LocalDateTime RENTED_AT = LocalDateTime.of(2026, 8, 11, 0, 0);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 10, 0, 0);

    Dataset create(String passwordHash) {
        List<BookSeed> books = new ArrayList<>(BOOK_COUNT);
        List<PageSeed> pages = new ArrayList<>(BOOK_COUNT * PAGES_PER_BOOK);
        for (int bookNumber = 1; bookNumber <= BOOK_COUNT; bookNumber++) {
            books.add(new BookSeed(
                    bookNumber,
                    "category-%02d".formatted((bookNumber - 1) % 10 + 1),
                    "성능 도서 %03d".formatted(bookNumber),
                    "성능 저자 %03d".formatted(bookNumber),
                    "결정적 성능 데이터 도서 %03d".formatted(bookNumber),
                    "/assets/covers/demo/category-%02d.svg".formatted((bookNumber - 1) % 10 + 1),
                    PAGES_PER_BOOK,
                    10_000));
            for (int pageNumber = 1; pageNumber <= PAGES_PER_BOOK; pageNumber++) {
                pages.add(new PageSeed(
                        pageId(bookNumber, pageNumber),
                        bookNumber,
                        pageNumber,
                        "성능 도서 %03d의 %d페이지 본문입니다.".formatted(bookNumber, pageNumber)));
            }
        }

        List<ReaderSeed> readers = new ArrayList<>(READER_COUNT);
        List<InkAccountSeed> inkAccounts = new ArrayList<>(READER_COUNT);
        List<InkPurchaseSeed> inkPurchases = new ArrayList<>(READER_COUNT);
        List<InkLedgerSeed> inkLedgers = new ArrayList<>(READER_COUNT + ACTIVE_READER_COUNT);
        List<PageRentalSeed> pageRentals = new ArrayList<>(ACTIVE_READER_COUNT);
        List<OwnershipPaymentSeed> ownershipPayments = new ArrayList<>(OWNED_READER_COUNT);
        List<BookOwnershipSeed> bookOwnerships = new ArrayList<>(OWNED_READER_COUNT);
        List<LibraryEntrySeed> libraryEntries =
                new ArrayList<>(ACTIVE_READER_COUNT + OWNED_READER_COUNT);

        for (int readerNumber = 1; readerNumber <= READER_COUNT; readerNumber++) {
            long readerId = 100_000L + readerNumber;
            int bookId = (readerNumber - 1) % BOOK_COUNT + 1;
            ReaderState state = ReaderState.fromReaderNumber(readerNumber);
            int balance = state == ReaderState.ACTIVE ? 99 : 100;

            readers.add(new ReaderSeed(
                    readerId,
                    "perf-reader-%04d@perf.ilgeobolkka.test".formatted(readerNumber),
                    passwordHash,
                    CREATED_AT));
            inkAccounts.add(new InkAccountSeed(200_000L + readerNumber, readerId, balance));
            inkPurchases.add(new InkPurchaseSeed(
                    300_000L + readerNumber,
                    readerId,
                    paymentId("10000000", readerNumber),
                    CREATED_AT));
            inkLedgers.add(InkLedgerSeed.grant(
                    400_000L + readerNumber,
                    readerId,
                    300_000L + readerNumber,
                    CREATED_AT));

            if (state == ReaderState.ACTIVE) {
                long rentalId = 500_000L + readerNumber;
                pageRentals.add(new PageRentalSeed(
                        rentalId,
                        readerId,
                        pageId(bookId, 1),
                        RENTED_AT,
                        EXPIRES_AT));
                inkLedgers.add(InkLedgerSeed.deduction(
                        600_000L + readerNumber,
                        readerId,
                        rentalId,
                        RENTED_AT));
                libraryEntries.add(new LibraryEntrySeed(
                        700_000L + readerNumber,
                        readerId,
                        bookId,
                        1,
                        RENTED_AT));
            } else if (state == ReaderState.OWNED) {
                long ownershipPaymentId = 800_000L + readerNumber;
                ownershipPayments.add(new OwnershipPaymentSeed(
                        ownershipPaymentId,
                        readerId,
                        bookId,
                        paymentId("20000000", readerNumber),
                        CREATED_AT));
                bookOwnerships.add(new BookOwnershipSeed(
                        900_000L + readerNumber,
                        readerId,
                        bookId,
                        ownershipPaymentId,
                        CREATED_AT));
                libraryEntries.add(new LibraryEntrySeed(
                        1_000_000L + readerNumber,
                        readerId,
                        bookId,
                        1,
                        CREATED_AT));
            }
        }

        return new Dataset(
                List.copyOf(books),
                List.copyOf(pages),
                List.copyOf(readers),
                List.copyOf(inkAccounts),
                List.copyOf(inkPurchases),
                List.copyOf(inkLedgers),
                List.copyOf(pageRentals),
                List.copyOf(ownershipPayments),
                List.copyOf(bookOwnerships),
                List.copyOf(libraryEntries));
    }

    static long pageId(int bookId, int pageNumber) {
        return (long) (bookId - 1) * PAGES_PER_BOOK + pageNumber;
    }

    private static String paymentId(String prefix, int readerNumber) {
        return "%s-0000-4000-8000-%012d".formatted(prefix, readerNumber);
    }

    enum ReaderState {
        NEW,
        ACTIVE,
        OWNED;

        static ReaderState fromReaderNumber(int readerNumber) {
            return values()[(readerNumber - 1) % values().length];
        }
    }

    record Dataset(
            List<BookSeed> books,
            List<PageSeed> pages,
            List<ReaderSeed> readers,
            List<InkAccountSeed> inkAccounts,
            List<InkPurchaseSeed> inkPurchases,
            List<InkLedgerSeed> inkLedgers,
            List<PageRentalSeed> pageRentals,
            List<OwnershipPaymentSeed> ownershipPayments,
            List<BookOwnershipSeed> bookOwnerships,
            List<LibraryEntrySeed> libraryEntries) {
    }

    record BookSeed(
            long id,
            String category,
            String title,
            String author,
            String description,
            String coverImagePath,
            int totalPageCount,
            int priceWon) {
    }

    record PageSeed(long id, long bookId, int pageNumber, String textContent) {
    }

    record ReaderSeed(long id, String email, String passwordHash, LocalDateTime createdAt) {
    }

    record InkAccountSeed(long id, long readerId, int balance) {
    }

    record InkPurchaseSeed(
            long id, long readerId, String paymentId, LocalDateTime createdAt) {
    }

    record InkLedgerSeed(
            long id,
            long readerId,
            String type,
            int amount,
            int balanceAfter,
            Long inkPurchaseId,
            Long pageRentalId,
            LocalDateTime occurredAt) {

        static InkLedgerSeed grant(
                long id, long readerId, long inkPurchaseId, LocalDateTime occurredAt) {
            return new InkLedgerSeed(
                    id, readerId, "GRANT", 100, 100, inkPurchaseId, null, occurredAt);
        }

        static InkLedgerSeed deduction(
                long id, long readerId, long pageRentalId, LocalDateTime occurredAt) {
            return new InkLedgerSeed(
                    id, readerId, "DEDUCTION", 1, 99, null, pageRentalId, occurredAt);
        }
    }

    record PageRentalSeed(
            long id,
            long readerId,
            long bookPageId,
            LocalDateTime rentedAt,
            LocalDateTime expiresAt) {
    }

    record OwnershipPaymentSeed(
            long id,
            long readerId,
            long bookId,
            String paymentId,
            LocalDateTime createdAt) {
    }

    record BookOwnershipSeed(
            long id,
            long readerId,
            long bookId,
            long ownershipPaymentId,
            LocalDateTime createdAt) {
    }

    record LibraryEntrySeed(
            long id,
            long readerId,
            long bookId,
            int lastPageNumber,
            LocalDateTime updatedAt) {
    }
}
