package com.example.ilgeobolkka.airoute.evaluation;

import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.repository.BookRepository;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 통과 report의 manifest 지원 도서 전체를 false에서 true로 한 번만 활성화한다. */
@Service
@Profile({"evaluation", "test"})
class AiRouteSupportActivationService {

    private final BookRepository bookRepository;
    private final OpenAiProperties openAiProperties;

    AiRouteSupportActivationService(
            BookRepository bookRepository, OpenAiProperties openAiProperties) {
        this.bookRepository = bookRepository;
        this.openAiProperties = openAiProperties;
    }

    @Transactional
    Activation activate(AiRouteEvaluationReport report) {
        if (report == null || !report.metrics().passed()) {
            throw new IllegalArgumentException("전체 품질 기준을 통과한 평가 report가 필요합니다.");
        }
        String dataPolicyVersion = openAiProperties.dataPolicyVersion();
        if (dataPolicyVersion == null || dataPolicyVersion.isBlank()) {
            throw new IllegalStateException("지원 활성화에는 OPENAI_DATA_POLICY_VERSION이 필요합니다.");
        }
        if (!Objects.equals(report.dataPolicyVersion(), dataPolicyVersion)) {
            throw new IllegalStateException("평가 report와 환경의 데이터 정책 프로필이 다릅니다.");
        }

        List<Long> targetBookIds = report.targetBookIds();
        List<Book> books = bookRepository.findActivationTargetsForUpdate(
                report.contentVersion(), report.dataPolicyVersion());
        List<Long> databaseBookIds = books.stream().map(Book::getId).toList();
        if (!databaseBookIds.equals(targetBookIds)) {
            throw new IllegalStateException(
                    "report와 DB의 contentVersion·데이터 정책·외부 전송 허용 기준 활성화 대상 전체가 "
                            + "일치하지 않습니다. 누락 또는 초과 대상이 있습니다.");
        }
        for (Book book : books) {
            requireActivationTarget(book, report.contentVersion(), report.dataPolicyVersion());
        }
        for (Book book : books) {
            book.activateAiRouteSupport();
        }
        bookRepository.flush();
        return new Activation(
                report.contentVersion(), report.dataPolicyVersion(), targetBookIds);
    }

    private void requireActivationTarget(
            Book book, String contentVersion, String dataPolicyVersion) {
        if (!Objects.equals(contentVersion, book.getContentVersion())) {
            throw new IllegalStateException(
                    "활성화 대상 도서의 contentVersion이 report와 다릅니다: " + book.getId());
        }
        if (!Objects.equals(dataPolicyVersion, book.getAiDataPolicyVersion())) {
            throw new IllegalStateException(
                    "활성화 대상 도서의 데이터 정책 프로필이 환경과 다릅니다: " + book.getId());
        }
        if (!book.isAiExternalTransferAllowed()) {
            throw new IllegalStateException(
                    "외부 전송이 허용되지 않은 도서는 활성화할 수 없습니다: " + book.getId());
        }
        if (book.isAiRouteSupported()) {
            throw new IllegalStateException(
                    "이미 활성화된 도서가 대상에 포함되어 있습니다: " + book.getId());
        }
    }

    record Activation(
            String contentVersion,
            String dataPolicyVersion,
            List<Long> activatedBookIds) {

        Activation {
            activatedBookIds = List.copyOf(activatedBookIds);
        }
    }
}
