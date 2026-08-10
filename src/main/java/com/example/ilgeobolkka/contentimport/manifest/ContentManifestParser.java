package com.example.ilgeobolkka.contentimport.manifest;

import static com.example.ilgeobolkka.contentimport.manifest.ContentManifest.AI_ROUTE_CONTENT_VERSION;
import static com.example.ilgeobolkka.contentimport.manifest.ContentManifest.INITIAL_CONTENT_VERSION;

import java.nio.charset.StandardCharsets;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

public final class ContentManifestParser {

    private final ObjectMapper objectMapper;
    private final ObjectReader strictReader;

    public ContentManifestParser(ObjectMapper objectMapper) {
        this.objectMapper = strictMapper(objectMapper);
        this.strictReader =
                this.objectMapper.reader(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public ContentManifest parseManifest(String manifestJson) {
        if (manifestJson == null) {
            throw new ContentManifestFormatException("콘텐츠 manifest JSON이 필요합니다.");
        }
        return parseManifest(manifestJson.getBytes(StandardCharsets.UTF_8));
    }

    public ContentManifest parseManifest(byte[] manifestBytes) {
        String contentVersion = readContentVersion(manifestBytes, "콘텐츠 manifest");
        return switch (contentVersion) {
            case INITIAL_CONTENT_VERSION -> parseInitialManifest(manifestBytes);
            case AI_ROUTE_CONTENT_VERSION -> parseAiRouteManifest(manifestBytes);
            default ->
                    throw new ContentManifestFormatException(
                            "지원하지 않는 콘텐츠 manifest 버전입니다: " + contentVersion);
        };
    }

    public AiRouteEvaluationDataset parseEvaluation(String evaluationJson) {
        if (evaluationJson == null) {
            throw new ContentManifestFormatException("AI 경로 evaluation JSON이 필요합니다.");
        }
        return parseEvaluation(evaluationJson.getBytes(StandardCharsets.UTF_8));
    }

    public AiRouteEvaluationDataset parseEvaluation(byte[] evaluationBytes) {
        String contentVersion = readContentVersion(evaluationBytes, "AI 경로 evaluation");
        if (!AI_ROUTE_CONTENT_VERSION.equals(contentVersion)) {
            throw new ContentManifestFormatException(
                    "AI 경로 evaluation의 contentVersion은 ai-route-v2여야 합니다.");
        }
        AiRouteEvaluationDataset evaluation =
                read(evaluationBytes, AiRouteEvaluationDataset.class, "AI 경로 evaluation");
        ContentManifestFormatValidator.validate(evaluation);
        return evaluation;
    }

    private InitialContentManifest parseInitialManifest(byte[] manifestBytes) {
        InitialContentManifest manifest =
                read(manifestBytes, InitialContentManifest.class, "초기 콘텐츠 manifest");
        ContentManifestFormatValidator.validate(manifest);
        return manifest;
    }

    private AiRouteContentManifest parseAiRouteManifest(byte[] manifestBytes) {
        AiRouteContentManifest manifest =
                read(manifestBytes, AiRouteContentManifest.class, "AI 경로 manifest");
        ContentManifestFormatValidator.validate(manifest);
        return manifest;
    }

    private String readContentVersion(byte[] content, String sourceName) {
        if (content == null || content.length == 0) {
            throw new ContentManifestFormatException(sourceName + " JSON이 필요합니다.");
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode version = root == null || !root.isObject() ? null : root.get("contentVersion");
            if (version == null || !version.isString() || version.stringValue().isBlank()) {
                throw new ContentManifestFormatException(
                        sourceName + "의 contentVersion은 필수 문자열입니다.");
            }
            return version.stringValue();
        } catch (JacksonException exception) {
            throw new ContentManifestFormatException(sourceName + "를 읽을 수 없습니다.", exception);
        }
    }

    private <T> T read(byte[] content, Class<T> type, String sourceName) {
        try {
            return strictReader.forType(type).readValue(content);
        } catch (JacksonException exception) {
            throw new ContentManifestFormatException(sourceName + " 형식이 올바르지 않습니다.", exception);
        }
    }

    private ObjectMapper strictMapper(ObjectMapper source) {
        return source.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .withCoercionConfig(
                        LogicalType.Integer,
                        config -> {
                            config.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                            config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
                            config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                            config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                        })
                .withCoercionConfig(
                        LogicalType.Boolean,
                        config -> {
                            config.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                            config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
                            config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                            config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                        })
                .withCoercionConfig(
                        LogicalType.Textual,
                        config -> {
                            config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                            config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                            config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                        })
                .withCoercionConfig(
                        LogicalType.Enum,
                        config -> {
                            config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
                            config.setCoercion(
                                    CoercionInputShape.Integer,
                                    CoercionAction.Fail);
                        })
                .build();
    }
}
