package ru.sberbank.pprb.agent.main;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/** Малый загрузчик набора и отчёт оценки, используемые только тестовым прогоном. */
final class ClassificationEvaluation {
    record Sample(String id, String text, String expectedActionCode) {}

    record Row(String id, String expectedActionCode, String actualActionCode, String category) {}

    private final List<Row> rows = new ArrayList<>();

    static List<Sample> readDataset(InputStream input) throws IOException {
        var json = new ObjectMapper();
        JsonNode tree = json.readTree(input);
        if (tree == null || !tree.isArray() || tree.isEmpty() || tree.size() > 10)
            throw new IllegalArgumentException("Набор должен содержать от 1 до 10 сообщений");
        var samples = new ArrayList<Sample>();
        var ids = new HashSet<String>();
        for (JsonNode node : tree) {
            if (!node.isObject()
                    || node.size() != 3
                    || !node.path("id").isTextual()
                    || !node.path("text").isTextual()
                    || !node.has("expectedActionCode")
                    || !(node.get("expectedActionCode").isNull()
                            || node.get("expectedActionCode").isTextual()
                                    && "status".equals(node.get("expectedActionCode").textValue())))
                throw new IllegalArgumentException("Неверная форма или метка примера");
            var sample = json.treeToValue(node, Sample.class);
            if (sample.id().isBlank() || sample.text().isBlank() || !ids.add(sample.id()))
                throw new IllegalArgumentException("Пустой или повторяющийся пример");
            samples.add(sample);
        }
        return List.copyOf(samples);
    }

    void add(Sample sample, String actual, boolean error) {
        String category =
                error || actual != null && !"status".equals(actual)
                        ? "ERROR"
                        : sample.expectedActionCode() != null
                                ? actual != null ? "TP" : "FN"
                                : actual != null ? "FP" : "TN";
        rows.add(new Row(sample.id(), sample.expectedActionCode(), actual, category));
    }

    ObjectNode report(String model, String dataset) {
        var json = new ObjectMapper();
        var report =
                json.createObjectNode()
                        .put("model", model)
                        .put("dataset", dataset)
                        .put("total", rows.size());
        for (String category : List.of("TP", "TN", "FP", "FN", "ERROR"))
            report.put(
                    "ERROR".equals(category) ? "errors" : category,
                    rows.stream().filter(r -> r.category().equals(category)).count());
        report.set("results", json.valueToTree(rows));
        return report;
    }
}
