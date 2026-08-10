package com.example.ilgeobolkka.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway.Embedding;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway.PageAnalysisInput;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway.PurposeInput;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiEmbeddingGatewayTest {

    @Test
    void purposeInputToString에는_종류와_길이만_포함한다() {
        PurposeInput input = new PurposeInput("노출하면 안 되는 독서 목적");

        assertThat(input.toString())
                .isEqualTo("PurposeInput[type=purpose, length=15]")
                .doesNotContain(input.normalizedPurpose());
    }

    @Test
    void pageAnalysisInputToString에는_종류와_길이만_포함한다() {
        PageAnalysisInput input = new PageAnalysisInput("노출하면 안 되는 페이지 분석 텍스트");

        assertThat(input.toString())
                .isEqualTo("PageAnalysisInput[type=pageAnalysis, length=20]")
                .doesNotContain(input.aiAnalysisText());
    }

    @Test
    void 입력값이_null이어도_toString은_안전하게_길이_0을_표시한다() {
        assertThat(new PurposeInput(null).toString())
                .isEqualTo("PurposeInput[type=purpose, length=0]");
    }

    @Test
    void embeddingToString에는_모델과_차원만_포함한다() {
        Embedding embedding = new Embedding(
                List.of(0.123456789, -0.987654321),
                "text-embedding-3-small",
                1536);

        assertThat(embedding.toString())
                .isEqualTo("Embedding[model=text-embedding-3-small, dimensions=1536]")
                .doesNotContain("0.123456789", "-0.987654321");
    }
}
