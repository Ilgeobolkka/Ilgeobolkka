package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * 생성 시작 판정. G07은 {@link Kind#NEW} 일 때만 외부 호출을 시작하고, 나머지는 이미 있는 결과나 거절
 * 사유를 그대로 응답으로 옮긴다.
 *
 * <p>{@code kind} 별로 채워지는 필드가 다르다. record 로 두면 canonical 생성자가 자동으로 public 이라
 * (JLS 8.10.4) 팩토리를 우회해 {@code EXISTING_FINAL} 인데 상태가 {@code GENERATING} 인 결과를 만들 수
 * 있다. 조합을 구조로 강제하려고 생성자를 숨긴 일반 클래스로 둔다. {@code AiRouteGenerationCommand} 를
 * record 로 두지 못한 것과 같은 이유다.
 *
 * <p>{@code equals}·{@code toString} 은 두지 않는다. 결과를 값으로 비교하거나 찍는 곳이 없고, 쓰지 않는
 * 구현을 손으로 적어 두면 계약이 바뀔 때 같이 틀어진다.
 */
public final class GenerationStartResult {

    private final Kind kind;
    private final UUID generationId;
    private final AiRouteGenerationStatus status;
    private final Instant expiresAt;

    private GenerationStartResult(
            Kind kind, UUID generationId, AiRouteGenerationStatus status, Instant expiresAt) {
        this.kind = kind;
        this.generationId = generationId;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public enum Kind {
        /** 이 요청이 새 생성을 만들었고 일일 횟수를 한 번 썼다. 외부 호출은 이 결과에서만 시작한다. */
        NEW,
        /** 같은 key·같은 입력의 생성이 아직 진행 중이다. 횟수를 더 쓰지 않는다. */
        EXISTING_GENERATING,
        /** 같은 key·같은 입력의 생성이 이미 끝났다. 저장된 상태를 그대로 돌려준다. */
        EXISTING_FINAL,
        /** 같은 key로 다른 입력이 들어왔다. {@code 409 AI_ROUTE_IDEMPOTENCY_KEY_REUSED}. */
        KEY_REUSED,
        /** 계정·UTC 날짜 생성 횟수를 다 썼다. {@code 429 AI_ROUTE_DAILY_LIMIT_EXCEEDED}. */
        DAILY_LIMIT
    }

    /** 방금 만든 {@code GENERATING} 행. 보관 만료는 완료 시점에 정해지므로 아직 없다. */
    static GenerationStartResult created(UUID generationId) {
        return new GenerationStartResult(
                Kind.NEW, generationId, AiRouteGenerationStatus.GENERATING, null);
    }

    /**
     * 이미 있던 행. 진행 중과 완료를 {@code status} 하나로 갈라 두 값이 어긋날 수 없게 한다. 호출자가
     * {@code kind} 를 직접 고르면 {@code EXISTING_FINAL} 인데 상태가 {@code GENERATING} 인 결과를 만들 수
     * 있다.
     */
    static GenerationStartResult existing(
            UUID generationId, AiRouteGenerationStatus status, Instant expiresAt) {
        Kind kind =
                status == AiRouteGenerationStatus.GENERATING
                        ? Kind.EXISTING_GENERATING
                        : Kind.EXISTING_FINAL;
        return new GenerationStartResult(kind, generationId, status, expiresAt);
    }

    /** 같은 key 다른 입력. 기존 생성의 식별자는 이 요청의 것이 아니므로 넘기지 않는다. */
    static GenerationStartResult keyReused() {
        return new GenerationStartResult(Kind.KEY_REUSED, null, null, null);
    }

    static GenerationStartResult dailyLimitExceeded() {
        return new GenerationStartResult(Kind.DAILY_LIMIT, null, null, null);
    }

    public Kind kind() {
        return kind;
    }

    /** {@code NEW}·{@code EXISTING_*} 에서만 값이 있다. */
    public UUID generationId() {
        return generationId;
    }

    /** {@code KEY_REUSED}·{@code DAILY_LIMIT} 은 {@code null} 이다. */
    public AiRouteGenerationStatus status() {
        return status;
    }

    /** 보관 만료 시각. 완료 상태에서만 값이 있고 {@code GENERATING} 은 아직 {@code null} 이다. */
    public Instant expiresAt() {
        return expiresAt;
    }
}
