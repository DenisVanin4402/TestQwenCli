package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Матрица качества не смешивает технический отказ с отрицательным предсказанием. */
class ClassificationEvaluationTest {
    @Test
    void countsAllCategoriesAndRejectsInvalidDatasets() throws Exception {
        var evaluation = new ClassificationEvaluation();
        var positive = new ClassificationEvaluation.Sample("p", "статус", "status");
        var negative = new ClassificationEvaluation.Sample("n", "справка", null);
        evaluation.add(positive, "status", false);
        evaluation.add(negative, null, false);
        evaluation.add(negative, "status", false);
        evaluation.add(positive, null, false);
        evaluation.add(positive, null, true);
        var report = evaluation.report("test", "synthetic");
        for (String counter : new String[] {"TP", "TN", "FP", "FN", "errors"})
            assertThat(report.path(counter).asInt()).isEqualTo(1);
        assertThat(report.path("total").asInt()).isEqualTo(5);
        assertThat(report.path("results")).hasSize(5);
        String valid = "{\"id\":\"a\",\"text\":\"текст\",\"expectedActionCode\":null}";
        assertThat(ClassificationEvaluation.readDataset(stream("[" + valid + "]"))).hasSize(1);
        for (String invalid :
                new String[] {
                    "[]",
                    "[" + valid + "," + valid + "]",
                    "[" + valid.replace("null", "\"recall\"") + "]",
                    "[" + String.join(",", java.util.Collections.nCopies(11, valid)) + "]",
                    "[{\"id\":\"a\",\"text\":\"текст\"}]"
                }) {
            assertThatThrownBy(() -> ClassificationEvaluation.readDataset(stream(invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private ByteArrayInputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
