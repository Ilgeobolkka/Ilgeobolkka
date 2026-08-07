package com.example.ilgeobolkka.airoute;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;

/**
 * 공급자·HTTP와 독립적인 생성 입력. 이후 단계는 이 값만 사용하고 목적을 다시 정규화하지 않는다.
 *
 * <p>필드 선언 순서를 멱등 요청 지문의 입력 순서로 쓰므로 순서를 바꾸면 같은 요청의 지문이 달라진다.
 * {@code purpose}는 {@link com.example.ilgeobolkka.airoute.service.AiRoutePurposeNormalizer}가
 * 정규화한 결과여야 한다.
 *
 * @param normalizedPurpose 정규화한 독서 목적
 * @param bookId 대상 도서
 * @param contentVersion 생성 시점 콘텐츠 버전
 * @param requestType 예산 입력인지 깊이 입력인지
 * @param maxAdditionalInk {@code INK_BUDGET}에서만 값을 가지는 추가 잉크 상한
 * @param depth {@code OWNED_DEPTH}에서만 값을 가지는 경로 깊이
 */
public record AiRouteGenerationCommand(
        String normalizedPurpose,
        long bookId,
        String contentVersion,
        AiRouteRequestType requestType,
        Integer maxAdditionalInk,
        AiRouteDepth depth) {

    /** 비소장 도서 예산 기본값 계산에 쓰는 상한. */
    public static final int DEFAULT_INK_BUDGET_CAP = 10;

    public AiRouteGenerationCommand {
        if (normalizedPurpose == null || normalizedPurpose.isBlank()) {
            throw new InvalidAiRouteGenerationInputException("정규화한 독서 목적이 필요합니다.");
        }
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new InvalidAiRouteGenerationInputException("콘텐츠 버전이 필요합니다.");
        }
        if (requestType == null) {
            throw new InvalidAiRouteGenerationInputException("예산 또는 깊이 입력 종류가 필요합니다.");
        }

        switch (requestType) {
            case INK_BUDGET -> {
                if (maxAdditionalInk == null) {
                    throw new InvalidAiRouteGenerationInputException("비소장 도서는 예산이 필요합니다.");
                }
                if (depth != null) {
                    throw new InvalidAiRouteGenerationInputException("예산과 깊이를 함께 보낼 수 없습니다.");
                }
                if (maxAdditionalInk < 0) {
                    throw new InvalidAiRouteGenerationInputException("예산은 0 이상이어야 합니다.");
                }
            }
            case OWNED_DEPTH -> {
                if (depth == null) {
                    throw new InvalidAiRouteGenerationInputException("소장 도서는 깊이가 필요합니다.");
                }
                if (maxAdditionalInk != null) {
                    throw new InvalidAiRouteGenerationInputException("예산과 깊이를 함께 보낼 수 없습니다.");
                }
            }
            // 입력 종류가 늘어나면 검증 없이 통과하지 않도록 명시적으로 거부한다.
            default -> throw new InvalidAiRouteGenerationInputException(
                    "지원하지 않는 입력 종류입니다. " + requestType);
        }
    }

    /**
     * 비소장 도서 입력. 예산 범위는 {@code 0..inkBalance}이다.
     *
     * @throws InvalidAiRouteGenerationInputException 예산이 범위를 벗어날 때
     */
    public static AiRouteGenerationCommand forInkBudget(
            String normalizedPurpose,
            long bookId,
            String contentVersion,
            int maxAdditionalInk,
            int inkBalance) {
        if (maxAdditionalInk > inkBalance) {
            throw new InvalidAiRouteGenerationInputException(
                    "예산은 현재 잉크 잔액 이하여야 합니다. 잔액 " + inkBalance + ", 입력 " + maxAdditionalInk);
        }
        return new AiRouteGenerationCommand(
                normalizedPurpose,
                bookId,
                contentVersion,
                AiRouteRequestType.INK_BUDGET,
                maxAdditionalInk,
                null);
    }

    /** 소장 도서 입력. 추가 비용이 없으므로 예산을 받지 않는다. */
    public static AiRouteGenerationCommand forOwnedDepth(
            String normalizedPurpose, long bookId, String contentVersion, AiRouteDepth depth) {
        return new AiRouteGenerationCommand(
                normalizedPurpose,
                bookId,
                contentVersion,
                AiRouteRequestType.OWNED_DEPTH,
                null,
                depth);
    }

    /**
     * 비소장 예산 기본값 {@code min(10, 잔액)}. 호출자가 잔액과 함께 요청할 때만 계산하며,
     * command 를 만든 뒤에는 입력을 다시 해석하지 않는다.
     */
    public static int defaultInkBudget(int inkBalance) {
        if (inkBalance < 0) {
            throw new InvalidAiRouteGenerationInputException("잉크 잔액은 0 이상이어야 합니다.");
        }
        return Math.min(DEFAULT_INK_BUDGET_CAP, inkBalance);
    }
}
