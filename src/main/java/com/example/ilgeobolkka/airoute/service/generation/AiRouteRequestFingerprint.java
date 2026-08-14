package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.AiRouteRequestType;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 생성 입력의 지문. 같은 멱등 키로 들어온 요청이 정말 같은 요청인지 판정하는 데만 쓴다.
 *
 * <p>입력은 canonical command 뿐이다. 요청 바디 원문이나 JSON map 순서는 보지 않는다. 원문을 섞으면 공백
 * 하나 차이로 같은 요청이 다른 지문이 되어 재시도가 {@code AI_ROUTE_IDEMPOTENCY_KEY_REUSED}로 튄다.
 *
 * <p>필드를 구분자 문자열로 잇지 않고 길이를 앞에 붙여 쓴다. {@code contentVersion="a|b"} 와
 * {@code contentVersion="a"}·다음 필드 {@code "b"}가 같은 바이트열이 되면 서로 다른 두 요청이 한 지문을
 * 공유하기 때문이다. 길이를 붙이면 어떤 조합도 바이트열이 겹치지 않는다.
 */
public final class AiRouteRequestFingerprint {

    private static final byte ABSENT = 0;
    private static final byte PRESENT = 1;
    private static final byte DEFAULT_INK_BUDGET = 2;

    private AiRouteRequestFingerprint() {}

    /**
     * {@code bookId · contentVersion · normalizedPurpose · requestType · depth}와 명시 예산 또는 기본 예산
     * 표식을 고정 순서로 넣은 SHA-256. 기본 예산의 계산값은 현재 잔액이지 HTTP 입력이
     * 아니므로 지문에 넣지 않는다.
     *
     * @return 소문자 hex 64자. {@code ai_route_generation.request_fingerprint CHAR(64)}에 그대로 들어간다.
     */
    public static String of(AiRouteGenerationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("생성 명령은 필수입니다.");
        }

        return ofCanonicalRequest(
                command.bookId(),
                command.contentVersion(),
                command.normalizedPurpose(),
                command.requestType(),
                command.maxAdditionalInk(),
                command.depth(),
                command.defaultInkBudget());
    }

    /** 저장된 canonical 값과 새 HTTP 입력의 예산 출처로 기존 지문을 재구성한다. */
    public static String ofCanonicalRequest(
            long bookId,
            String contentVersion,
            String normalizedPurpose,
            AiRouteRequestType requestType,
            Integer maxAdditionalInk,
            AiRouteDepth depth,
            boolean defaultInkBudget) {
        if (contentVersion == null
                || normalizedPurpose == null
                || requestType == null) {
            throw new IllegalArgumentException("canonical 생성 입력은 필수입니다.");
        }

        ByteArrayOutputStream input = new ByteArrayOutputStream();
        writeLong(input, bookId);
        writeString(input, contentVersion);
        writeString(input, normalizedPurpose);
        writeString(input, requestType.name());
        writeNullableInt(input, defaultInkBudget ? null : maxAdditionalInk);
        writeNullableDepth(input, depth);
        // 기존 명시 예산 지문은 바꾸지 않는다. 기본 예산은 현재 잔액 대신 표식으로 구분한다.
        if (defaultInkBudget) {
            input.write(DEFAULT_INK_BUDGET);
        }

        return HexFormat.of().formatHex(sha256(input.toByteArray()));
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        out.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    /** 길이를 먼저 쓴다. 그래야 이어 붙인 바이트열에서 필드 경계가 유일하게 결정된다. */
    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    /** null 여부 자체가 지문 입력이다. 예산 0과 예산 없음은 다른 요청이다. */
    private static void writeNullableInt(ByteArrayOutputStream out, Integer value) {
        if (value == null) {
            out.write(ABSENT);
            return;
        }
        out.write(PRESENT);
        writeInt(out, value);
    }

    private static void writeNullableDepth(ByteArrayOutputStream out, AiRouteDepth value) {
        if (value == null) {
            out.write(ABSENT);
            return;
        }
        out.write(PRESENT);
        writeString(out, value.name());
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 은 모든 Java 플랫폼이 제공해야 하는 알고리즘이다.
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", impossible);
        }
    }
}
