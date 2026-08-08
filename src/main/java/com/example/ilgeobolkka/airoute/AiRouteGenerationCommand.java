package com.example.ilgeobolkka.airoute;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRoutePurposeException;
import com.example.ilgeobolkka.airoute.service.AiRoutePurposeNormalizer;
import java.util.Objects;

/**
 * 공급자·HTTP와 독립적인 생성 입력. 생성 경로는 팩토리 두 개뿐이고 팩토리가 raw purpose 를 받아 직접
 * 정규화하므로, 정규화·검증을 건너뛴 command 는 만들어질 수 없다. 이후 단계는 이 값만 사용하고 목적을
 * 다시 정규화하지 않는다.
 *
 * <p>record 가 아닌 이유는 {@link #forInkBudget}의 잔액 검사가 command 필드에 없는 {@code inkBalance}를
 * 참조하기 때문이다. public record 는 canonical 생성자를 좁힐 수 없어 잔액 검사를 건너뛰는 경로가 남는다.
 *
 * <p>필드 선언 순서는 G05의 지문 입력 순서와 같다. 다만 지문 계산은 선언 순서에 기대지 말고 각 값을
 * 명시적으로 나열해야 한다.
 */
public final class AiRouteGenerationCommand {

    /** 비소장 도서 예산 기본값 계산에 쓰는 상한. */
    public static final int DEFAULT_INK_BUDGET_CAP = 10;

    private final long bookId;
    private final String contentVersion;
    private final String normalizedPurpose;
    private final AiRouteRequestType requestType;
    private final Integer maxAdditionalInk;
    private final AiRouteDepth depth;

    /**
     * 예산·깊이 배타 규칙은 팩토리가 어느 쪽에 null 을 넣을지 정해 구조로 보장하므로 여기서 다시
     * 검사하지 않는다. 두 팩토리를 거치지 않고 이 생성자에 닿을 방법은 없다.
     */
    private AiRouteGenerationCommand(
            long bookId,
            String contentVersion,
            String rawPurpose,
            AiRouteRequestType requestType,
            Integer maxAdditionalInk,
            AiRouteDepth depth) {
        String normalized = AiRoutePurposeNormalizer.normalize(rawPurpose);
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new InvalidAiRouteGenerationInputException("콘텐츠 버전이 필요합니다.");
        }

        this.bookId = bookId;
        this.contentVersion = contentVersion;
        this.normalizedPurpose = normalized;
        this.requestType = requestType;
        this.maxAdditionalInk = maxAdditionalInk;
        this.depth = depth;
    }

    /**
     * 비소장 도서 입력. 목적은 여기서 정규화하고, 예산 범위는 {@code 0..inkBalance}로 잔액과 함께 검사한다.
     *
     * @param rawPurpose 정규화 전 독서 목적
     * @throws InvalidAiRouteGenerationInputException 예산이 {@code 0..inkBalance}를 벗어나거나 콘텐츠
     *     버전이 비었을 때
     * @throws InvalidAiRoutePurposeException 목적이 비었거나 200 code point를 넘을 때
     */
    public static AiRouteGenerationCommand forInkBudget(
            long bookId, String contentVersion, String rawPurpose, int maxAdditionalInk, int inkBalance) {
        if (maxAdditionalInk < 0) {
            throw new InvalidAiRouteGenerationInputException("예산은 0 이상이어야 합니다.");
        }
        if (maxAdditionalInk > inkBalance) {
            throw new InvalidAiRouteGenerationInputException(
                    "예산은 현재 잉크 잔액 이하여야 합니다. 잔액 " + inkBalance + ", 입력 " + maxAdditionalInk);
        }
        return new AiRouteGenerationCommand(
                bookId, contentVersion, rawPurpose, AiRouteRequestType.INK_BUDGET, maxAdditionalInk, null);
    }

    /**
     * 소장 도서 입력. 추가 비용이 없으므로 예산을 받지 않는다.
     *
     * @param rawPurpose 정규화 전 독서 목적
     * @throws InvalidAiRouteGenerationInputException 깊이가 없거나 콘텐츠 버전이 비었을 때
     * @throws InvalidAiRoutePurposeException 목적이 비었거나 200 code point를 넘을 때
     */
    public static AiRouteGenerationCommand forOwnedDepth(
            long bookId, String contentVersion, String rawPurpose, AiRouteDepth depth) {
        if (depth == null) {
            throw new InvalidAiRouteGenerationInputException("소장 도서는 깊이가 필요합니다.");
        }
        return new AiRouteGenerationCommand(
                bookId, contentVersion, rawPurpose, AiRouteRequestType.OWNED_DEPTH, null, depth);
    }

    /**
     * 비소장 예산 기본값 {@code min(10, 잔액)}. 호출자가 잔액과 함께 요청할 때만 계산하며,
     * command 를 만든 뒤에는 입력을 다시 해석하지 않는다.
     *
     * @throws IllegalStateException 잔액이 음수일 때. 잉크 잔액은 DB CHECK 제약
     *     {@code ck_ink_account_balance_non_negative}로 0 이상이 보장되므로, 음수는 사용자 입력 오류가
     *     아니라 시스템 불변식 위반이다. 사용자 입력 오류로 던지면 서버 버그가 400으로 나간다.
     */
    public static int defaultInkBudget(int inkBalance) {
        if (inkBalance < 0) {
            throw new IllegalStateException("잉크 잔액이 음수입니다. " + inkBalance);
        }
        return Math.min(DEFAULT_INK_BUDGET_CAP, inkBalance);
    }

    public long bookId() {
        return bookId;
    }

    public String contentVersion() {
        return contentVersion;
    }

    public String normalizedPurpose() {
        return normalizedPurpose;
    }

    public AiRouteRequestType requestType() {
        return requestType;
    }

    public Integer maxAdditionalInk() {
        return maxAdditionalInk;
    }

    public AiRouteDepth depth() {
        return depth;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AiRouteGenerationCommand that)) {
            return false;
        }
        return bookId == that.bookId
                && contentVersion.equals(that.contentVersion)
                && normalizedPurpose.equals(that.normalizedPurpose)
                && requestType == that.requestType
                && Objects.equals(maxAdditionalInk, that.maxAdditionalInk)
                && depth == that.depth;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                bookId, contentVersion, normalizedPurpose, requestType, maxAdditionalInk, depth);
    }

    /** 목적은 로그에 남기지 않으므로 길이만 드러낸다. */
    @Override
    public String toString() {
        return ("AiRouteGenerationCommand[bookId=%d, contentVersion=%s, purposeCodePoints=%d, "
                        + "requestType=%s, maxAdditionalInk=%s, depth=%s]")
                .formatted(
                        bookId,
                        contentVersion,
                        normalizedPurpose.codePointCount(0, normalizedPurpose.length()),
                        requestType,
                        maxAdditionalInk,
                        depth);
    }
}
