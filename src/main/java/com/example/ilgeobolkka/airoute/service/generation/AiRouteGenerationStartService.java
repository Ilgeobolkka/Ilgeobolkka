package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteDailyUsage;
import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.repository.AiRouteDailyUsageRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationItemRepository;
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
    private final AiRouteGenerationItemRepository generationItemRepository;
    private final AiRouteDailyUsageRepository dailyUsageRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 아직 유효한 같은 키가 있으면 현재 도서·권한을 다시 검증하기 전에 기존 판정을 반환한다.
     *
     * <p>멱등 재요청은 완료 뒤 콘텐츠 버전이나 권한이 바뀌어도 최초 상태·결과를 재생해야 한다. 기존 행이
     * 없으면 아무것도 만들거나 계수하지 않고 빈 결과를 반환하며, 호출자는 현재 입력 snapshot을 준비한 뒤
     * {@link #startBefore}를 호출한다. 그사이 동시 요청이 행을 만들 수 있으므로 {@code startBefore}도 기존
     * 행을 다시 확인하는 최종 판정 책임을 그대로 가진다.
     */
    @Transactional(propagation = Propagation.NEVER)
    public Optional<GenerationStartResult> findExisting(
            long readerId, UUID idempotencyKey, AiRouteGenerationCommand command) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("멱등 키는 필수입니다.");
        }
        String requestFingerprint = AiRouteRequestFingerprint.of(command);
        return transactionTemplate.execute(
                status -> findExistingForUpdate(readerId, idempotencyKey)
                        .filter(this::isUsable)
                        .map(generation -> resultOf(generation, requestFingerprint)));
    }

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
        return start(readerId, idempotencyKey, command, null);
    }

    /** 기존 멱등 결과는 기한 후에도 재생하고, 새 요청만 외부 호출 마감 시각 전에 시작한다. */
    @Transactional(propagation = Propagation.NEVER)
    public GenerationStartResult startBefore(
            long readerId,
            UUID idempotencyKey,
            AiRouteGenerationCommand command,
            Instant externalCallDeadline) {
        if (externalCallDeadline == null) {
            throw new IllegalArgumentException("외부 호출 마감 시각은 필수입니다.");
        }
        return start(readerId, idempotencyKey, command, externalCallDeadline);
    }

    private GenerationStartResult start(
            long readerId,
            UUID idempotencyKey,
            AiRouteGenerationCommand command,
            Instant externalCallDeadline) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("멱등 키는 필수입니다.");
        }
        String requestFingerprint = AiRouteRequestFingerprint.of(command);

        try {
            return transactionTemplate.execute(
                    status -> startOnce(
                            readerId,
                            idempotencyKey,
                            command,
                            requestFingerprint,
                            externalCallDeadline));
        } catch (ExternalCallDeadlineExceededException exception) {
            return GenerationStartResult.timedOut();
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
     * 않는 오류를 성공으로 둔갑시키지 않도록 원래 예외를 그대로 올린다. 만료한 행도 없는 것으로 본다.
     * 만료 행을 지운 뒤 insert 가 다른 제약으로 실패하면 rollback 으로 그 행이 되살아나는데, 그것을
     * 기존 결과라고 돌려주면 원래 예외까지 삼킨다.
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
                                        .filter(this::isUsable)
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
            Instant externalCallDeadline) {
        Optional<AiRouteGeneration> existing = findExistingForUpdate(readerId, idempotencyKey);
        if (existing.isPresent()) {
            AiRouteGeneration generation = existing.get();
            if (isUsable(generation)) {
                // 아직 유효한 요청은 성공이든 실패든 횟수를 다시 쓰지 않는다.
                return resultOf(generation, requestFingerprint);
            }
            discardExpired(generation);
        }

        requireBeforeDeadline(externalCallDeadline);
        // 잠금 순서는 사용량 행 → 생성 행 insert 다. 같은 독자의 동시 요청은 대개 사용량 행 하나에
        // 줄을 서지만, UTC 자정을 사이에 둔 두 요청은 서로 다른 사용량 행을 잠그므로 줄이 서지 않는다.
        // 그렇게 겹친 insert는 unique key 경합이 되고, start()가 기존 행 재조회로 수렴시킨다.
        AiRouteDailyUsage usage = lockCurrentDailyUsage(readerId);
        // 사용량 잠금을 기다리다 만료했으면 예외로 transaction 전체를 rollback한다.
        requireBeforeDeadline(externalCallDeadline);
        if (usage.getGenerationCount() >= DAILY_GENERATION_LIMIT) {
            return existingOrDailyLimit(readerId, idempotencyKey, requestFingerprint);
        }
        usage.increment();

        // 계수 날짜를 확정한 뒤에 읽는다. 그래서 DATE(created_at) >= usage_date 가 항상 성립하고
        // 반대는 불가능하다. created_at 으로 일일 집계를 재구성하면 ai_route_daily_usage 보다 뒤로
        // 밀릴 수는 있어도 앞당겨지지 않는다.
        Instant startedAt = clock.instant();
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

    private void requireBeforeDeadline(Instant externalCallDeadline) {
        if (externalCallDeadline != null && !clock.instant().isBefore(externalCallDeadline)) {
            throw new ExternalCallDeadlineExceededException();
        }
    }

    /**
     * 한도에 닿았을 때, 거절하기 전에 같은 키의 생성이 이미 있는지 다시 본다.
     *
     * <p>사용량 잠금을 기다리는 동안 같은 멱등 키의 요청이 그날의 마지막 한 건을 가져갔을 수 있다.
     * insert 까지 가는 경로는 unique key 경합이 기존 행으로 수렴시켜 주지만, 이 경로는 아무것도 넣지
     * 않고 돌아가므로 스스로 확인해야 한다. 확인하지 않으면 이미 시작된 생성을 두고 클라이언트에게
     * 한도 초과로 거절했다고 알려, 15분 안의 같은 요청은 저장된 상태를 돌려준다는 계약을 깬다.
     *
     * <p>{@link #findExistingForUpdate} 를 쓰지 않고 잠금 조회로 바로 간다. 이 transaction 의 read view는
     * 맨 앞의 존재 확인에서 이미 만들어졌으므로, 일반 조회로는 그 뒤에 commit 된 행을 볼 수 없다.
     * 잠금 조회만 최신 commit 을 읽는다.
     *
     * <p>다른 두 조회와 <b>같이</b> 만료 필터를 둔다. 앞머리 존재 확인이 참이었다면 만료 행은 이미 그
     * 자리에서 지워졌지만, 거짓이었다면 이 transaction 의 read view 뒤에 commit 된 행이 여기서 처음
     * 보인다. 그 행이 만료일 시간은 충분하다 — 이 메서드에 닿기 전에 사용량 행 잠금을 기다리고, 그
     * 대기는 앞선 transaction 이 쥔 시간만큼 길어진다. 기다리는 동안 같은 키의 요청이 시작하고, 별개
     * transaction 이 그것을 완료하고, 보관 기간까지 지날 수 있다. 그때 이 잠금 조회는 read view 가
     * 아니라 최신 commit 을 읽으므로 만료한 결과를 그대로 집어 온다.
     *
     * <p>걸러진 뒤 한도 초과로 거절하는 것이 맞는 답이다. 만료한 멱등 상태는 없는 것이고, 없으면 이
     * 요청은 새 요청이며, 새 요청에 쓸 횟수가 남아 있지 않다.
     *
     * <p>{@link #discardExpired} 로 지우지는 않는다. 이 경로는 아무것도 넣지 않고 거절만 하고 돌아간다.
     * 그 행은 정리 배치가 지우거나, 한도가 풀린 뒤 같은 키의 다음 시작이 지운다. 만료 <b>판정</b>이
     * 정리 실행 여부에 기대지 않는다는 조건은 여기서도 지켜진다.
     *
     * <p>없는 행을 잠그면 gap lock 이 남지만 여기서는 교착으로 가지 않는다. 이 경로는 잠금을 잡은 뒤
     * 아무것도 기다리지 않고 바로 돌아가므로 대기 고리가 만들어지지 않는다. 한도에 닿은 독자만
     * 지나가는 길이라 빈도도 낮다.
     */
    private GenerationStartResult existingOrDailyLimit(
            long readerId, UUID idempotencyKey, String requestFingerprint) {
        return generationRepository
                .findByReaderIdAndIdempotencyKeyForUpdate(readerId, idempotencyKey)
                .filter(this::isUsable)
                .map(generation -> resultOf(generation, requestFingerprint))
                .orElseGet(GenerationStartResult::dailyLimitExceeded);
    }

    /**
     * 만료한 멱등 상태는 남아 있어도 없는 것으로 본다. 15분이 지나면 임시 결과와 멱등 상태를 함께
     * 지우고 같은 키가 다시 오면 새 요청으로 취급한다는 것이 계약이며, 그 판정이 정리 배치가 돌았는지에
     * 달려 있으면 안 된다.
     *
     * <p>{@code expiresAt} 이 {@code null} 인 {@code GENERATING} 은 아직 만료 대상이 아니다.
     */
    private boolean isUsable(AiRouteGeneration generation) {
        Instant expiresAt = generation.getExpiresAt();
        return expiresAt == null || clock.instant().isBefore(expiresAt);
    }

    /**
     * 만료한 행을 그 자리에서 지운다. 남겨 두면 뒤따르는 새 생성 insert 가
     * {@code uk_ai_route_generation_reader_idempotency} 에 걸려 같은 키로 다시 시작할 수 없다.
     *
     * <p>지우고 바로 flush 한다. 미루면 Hibernate 가 같은 flush 안에서 INSERT 를 DELETE 보다 먼저
     * 내보내 unique key 가 깨진다.
     *
     * <p>일일 사용량은 되돌리지 않는다. 만료 전 요청은 이미 한 건으로 계수됐고, 같은 키로 다시 오는
     * 요청도 새 요청 한 건으로 계수하는 것이 계약이다. 저장 경로는 이 행을 외래 키로 참조하지 않으므로
     * 기한 없는 경로는 그대로 남는다.
     */
    private void discardExpired(AiRouteGeneration generation) {
        generationItemRepository.deleteByGenerationId(generation.getGenerationId());
        generationRepository.delete(generation);
        generationRepository.flush();
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
     * 지금 UTC 날짜의 사용량 행을 잠근다. 잠근 뒤에 날짜를 다시 확인해서, 잠금을 기다리는 동안 자정을
     * 넘겼으면 새 날짜의 행으로 옮겨 잠근다.
     *
     * <p>진입 시점 날짜로 고정하면 앞선 요청이 잠금을 오래 쥐는 사이 자정이 지났을 때 이미 초기화된
     * 어제 한도로 거절한다. PRD는 횟수를 매일 {@code 00:00 UTC}에 초기화하고 계수를 첫 외부 호출 직전
     * 시점의 UTC 날짜로 규정한다.
     *
     * <p>이미 잡은 잠금은 transaction 이 끝나야 풀리므로 지나간 날짜의 행도 함께 쥔 채로 진행한다.
     * 넘긴 뒤에는 한 번만 더 잠그고 끝낸다. 반복해도 정밀도는 늘지 않는다 — 재확인이 보장하는 것은
     * "잠금이 승인된 순간 날짜가 유효했다" 이지 "계수하는 순간 유효하다" 가 아니라서
     * {@code increment} 와 commit 사이의 창은 그대로 남는다. 반면 시계가 뒤로 가면 반복은 끝나지 않고
     * 행 잠금만 쌓는다. 한 번으로 묶으면 그런 시계에서도 피해가 행 두 개와 재시도 한 번에 그친다.
     *
     * <p>자정을 넘긴 요청은 어제 날짜에 0회 행을 남긴다. 계수 관점에서는 행이 없는 것과 같은 값이지만,
     * 다른 경로는 실패 시 rollback 되므로 <b>커밋된 0회 행이 생기는 경로는 이 자정 통과뿐이다.</b>
     * 사실상 "여기서 transaction 이 자정을 넘었다" 는 표식이다. 정리 배치는 0회 행을 지워도 잃는 정보가
     * 없고, 이 테이블을 {@code COUNT(*)} 로 세는 지표는 활동한 날을 과다 계상한다.
     */
    private AiRouteDailyUsage lockCurrentDailyUsage(long readerId) {
        LocalDate usageDate = currentUsageDate();
        AiRouteDailyUsage usage = lockDailyUsage(readerId, usageDate);

        LocalDate confirmed = currentUsageDate();
        if (usageDate.equals(confirmed)) {
            return usage;
        }
        // 자정을 넘긴 뒤 한 번 더 잠근다. 한 transaction 이 자정을 두 번 넘지는 않으므로 여기서
        // 끝난다. 두 번 어긋난다면 시계가 뒤로 갔다는 뜻이라 잠금을 더 쌓지 않고 멈춘다.
        return lockDailyUsage(readerId, confirmed);
    }

    private LocalDate currentUsageDate() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
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

    private static final class ExternalCallDeadlineExceededException extends RuntimeException {}
}
