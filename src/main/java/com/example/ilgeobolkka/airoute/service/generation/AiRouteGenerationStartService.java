package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteDailyUsage;
import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.repository.AiRouteDailyUsageRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 생성 시작을 멱등 처리하고 하루 생성 횟수를 계수한다. 외부 호출은 하지 않는다.
 *
 * <p>사용량 증가와 {@code GENERATING} insert는 한 transaction이고, 이 메서드가 값을 돌려줄 때는 이미
 * commit이 끝나 있다. G07은 {@link GenerationStartResult.Kind#NEW} 를 받은 뒤에만 OpenAI를 부른다. 아직
 * commit되지 않은 행을 근거로 외부 호출을 시작하면 rollback된 요청이 과금·횟수만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AiRouteGenerationStartService {

    /** 계정·UTC 날짜별 상한. 성공·실패와 무관하게 시작 시점에 계수한다. */
    public static final int DAILY_GENERATION_LIMIT = 10;

    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteDailyUsageRepository dailyUsageRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * {@code Propagation.NEVER}는 호출자가 자기 transaction으로 감싸는 것을 막는다. 감싸면 아래
     * {@link TransactionTemplate}이 그 transaction에 합류해 commit이 이 메서드 반환 뒤로 밀리고, G07이
     * 아직 확정되지 않은 {@code NEW}로 외부 호출을 시작하게 된다. 계약을 주석이 아니라 예외로 지킨다.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException 호출자가 transaction 안일 때
     */
    @Transactional(propagation = Propagation.NEVER)
    public GenerationStartResult start(
            long readerId, UUID idempotencyKey, AiRouteGenerationCommand command) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("멱등 키는 필수입니다.");
        }
        String requestFingerprint = AiRouteRequestFingerprint.of(command);
        Instant startedAt = clock.instant();
        // 요청이 시작된 UTC 날짜로 계수한다. 뒤에 수렴 경로를 타더라도 다시 계산하지 않는다.
        LocalDate usageDate = LocalDate.ofInstant(startedAt, ZoneOffset.UTC);

        try {
            return transactionTemplate.execute(
                    status ->
                            startOnce(
                                    readerId,
                                    idempotencyKey,
                                    command,
                                    requestFingerprint,
                                    usageDate,
                                    startedAt));
        } catch (DataIntegrityViolationException conflict) {
            return convergeOnExisting(readerId, idempotencyKey, requestFingerprint, conflict);
        }
    }

    /**
     * 동시 요청이 {@code uk_ai_route_generation_reader_idempotency}를 먼저 차지했을 때 그 행으로 수렴한다.
     * duplicate key 예외가 올라왔다는 것은 상대 transaction이 이미 commit 됐다는 뜻이므로 새 transaction의
     * 재조회는 그 행을 본다. 같은 입력이면 저장된 상태를, 다른 입력이면 키 재사용을 돌려준다.
     *
     * <p>행이 없으면 unique key 경합이 아니라 다른 제약 위반이다. 도서 외래 키처럼 다시 읽어도 달라지지
     * 않는 오류를 성공으로 둔갑시키지 않도록 원래 예외를 그대로 올린다.
     */
    private GenerationStartResult convergeOnExisting(
            long readerId,
            UUID idempotencyKey,
            String requestFingerprint,
            DataIntegrityViolationException conflict) {
        GenerationStartResult converged =
                transactionTemplate.execute(
                        status ->
                                findExistingForUpdate(readerId, idempotencyKey)
                                        .map(
                                                generation ->
                                                        resultOf(generation, requestFingerprint))
                                        .orElse(null));
        if (converged == null) {
            throw conflict;
        }
        return converged;
    }

    private GenerationStartResult startOnce(
            long readerId,
            UUID idempotencyKey,
            AiRouteGenerationCommand command,
            String requestFingerprint,
            LocalDate usageDate,
            Instant startedAt) {
        Optional<AiRouteGeneration> existing = findExistingForUpdate(readerId, idempotencyKey);
        if (existing.isPresent()) {
            // 이미 있는 요청은 성공이든 실패든 횟수를 다시 쓰지 않는다.
            return resultOf(existing.get(), requestFingerprint);
        }

        // 잠금 순서는 사용량 행 → 생성 행 insert 다. 같은 독자의 동시 요청은 대개 사용량 행 하나에
        // 줄을 서지만, UTC 자정을 사이에 둔 두 요청은 서로 다른 사용량 행을 잠그므로 줄이 서지 않는다.
        // 그렇게 겹친 insert는 unique key 경합이 되고, start()가 기존 행 재조회로 수렴시킨다.
        AiRouteDailyUsage usage = lockDailyUsage(readerId, usageDate);
        if (usage.getGenerationCount() >= DAILY_GENERATION_LIMIT) {
            return GenerationStartResult.dailyLimitExceeded();
        }
        usage.increment();

        UUID generationId = UUID.randomUUID();
        // flush를 미루면 insert가 commit 시점에 실행돼 제약 위반이 transaction 종료 예외로 뒤바뀐다.
        // 여기서 흘려보내야 호출자가 받는 예외 종류가 일정하고, 사용량 증가와 함께 되돌아간다.
        generationRepository.saveAndFlush(
                AiRouteGeneration.start(
                        generationId,
                        readerId,
                        idempotencyKey,
                        requestFingerprint,
                        command,
                        startedAt));
        return GenerationStartResult.created(generationId);
    }

    /**
     * 있을 때만 잠근다. 없는 행에 바로 잠금을 걸면 InnoDB가 그 자리에 gap lock을 남겨, 그 gap 안에
     * insert하려는 다른 독자의 동시 요청과 교착한다. 존재 확인은 엔티티를 적재하지 않는 조회여야
     * 뒤따르는 잠금 조회가 잠근 뒤의 값을 읽는다.
     *
     * <p>두 조회 사이에 행이 사라지는 경우는 만료 정리뿐이다. 그때는 없는 것으로 보고 새 요청으로
     * 처리하는 것이 맞다.
     */
    private Optional<AiRouteGeneration> findExistingForUpdate(long readerId, UUID idempotencyKey) {
        if (!generationRepository.existsByReaderIdAndIdempotencyKey(readerId, idempotencyKey)) {
            return Optional.empty();
        }
        return generationRepository.findByReaderIdAndIdempotencyKeyForUpdate(
                readerId, idempotencyKey);
    }

    /**
     * 없으면 0회로 만든 뒤 잠근다. 만드는 것과 잠그는 것을 한 문장으로 합칠 수 없어 두 번 부르지만,
     * 앞 문장이 unique key 충돌을 무시하므로 두 요청이 동시에 들어와도 뒤 문장은 같은 행을 본다.
     */
    private AiRouteDailyUsage lockDailyUsage(long readerId, LocalDate usageDate) {
        dailyUsageRepository.insertUnusedDayIfAbsent(readerId, usageDate);
        return dailyUsageRepository
                .findByReaderIdAndUsageDateForUpdate(readerId, usageDate)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "사용량 행을 만든 직후 찾지 못했습니다. reader "
                                                + readerId
                                                + ", "
                                                + usageDate));
    }

    /**
     * 같은 키로 다른 입력이 오면 앞선 요청의 결과를 돌려주지 않는다. 클라이언트가 바뀐 입력의 결과라고
     * 믿게 되기 때문이다.
     */
    private static GenerationStartResult resultOf(
            AiRouteGeneration generation, String requestFingerprint) {
        if (!generation.getRequestFingerprint().equals(requestFingerprint)) {
            return GenerationStartResult.keyReused();
        }
        return GenerationStartResult.existing(
                generation.getGenerationId(), generation.getStatus(), generation.getExpiresAt());
    }
}
