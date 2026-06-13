package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.gateway.support.GatewayTestRequests;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"external-gateway.repository.type=memory",
		"external-gateway.async.dispatcher-enabled=false",
		"external-gateway.callback.delivery-enabled=false"
})
@AutoConfigureMockMvc
class ExternalGatewayOpenApiContractTest {

	private static final List<String> OPENAPI_FILE_NAMES = List.of(
			"external-gateway-sync.yaml",
			"external-gateway-async.yaml",
			"external-gateway-callback.yaml"
	);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void springdocOpenApiDocumentIsAvailableForInternalDiagnostics() throws Exception {
		JsonNode openApi = springdocOpenApi();

		assertThat(openApi.path("openapi").asText()).isNotBlank();
		assertThat(fieldNames(openApi.path("paths")))
				.contains("/v1/external/sync", "/v1/external/async");
	}

	@Test
	void callbackOpenApiDocumentMatchesSerializedCallbackPayload() {
		Map<String, Object> callbackDocument = loadOpenApiDocument("external-gateway-callback.yaml");
		JsonNode callbackPayload = objectMapper.valueToTree(GatewayTestRequests.doneCallbackPayload(123));

		assertThat(fieldNames(callbackPayload))
				.containsAll(documentedRequiredFields(callbackDocument, "ExternalGatewayCallback"));
		assertThat(fieldNames(callbackPayload))
				.containsAll(documentedSchemaProperties(callbackDocument, "ExternalGatewayCallback"));
	}

	@Test
	void resourceOpenApiDocumentsMatchDocumentationMirror() throws Exception {
		Path resourcesDirectory = resourcesOpenApiDirectory();
		Path documentationDirectory = documentationOpenApiDirectory();

		for (String fileName : OPENAPI_FILE_NAMES) {
			Path resourcePath = resourcesDirectory.resolve(fileName);
			Path documentationPath = documentationDirectory.resolve(fileName);

			assertThat(resourcePath).isRegularFile();
			assertThat(documentationPath).isRegularFile();
			assertThat(Files.readString(documentationPath, StandardCharsets.UTF_8))
					.as("%s в docs совпадает с рабочей копией в resources", fileName)
					.isEqualTo(Files.readString(resourcePath, StandardCharsets.UTF_8));
		}
	}

	private JsonNode springdocOpenApi() throws Exception {
		MvcResult result = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private static Set<String> documentedSchemaProperties(Map<String, Object> document, String schemaName) {
		return mapAt(document, "components", "schemas", schemaName, "properties").keySet();
	}

	private static Set<String> documentedRequiredFields(Map<String, Object> document, String schemaName) {
		Object required = mapAt(document, "components", "schemas", schemaName).get("required");
		if (required == null) {
			return Set.of();
		}
		assertThat(required).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<String> fields = (List<String>) required;
		return new LinkedHashSet<>(fields);
	}

	private static Map<String, Object> loadOpenApiDocument(String fileName) {
		Path path = resourcesOpenApiDirectory().resolve(fileName);
		Yaml yaml = new Yaml();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Object value = yaml.load(reader);
			return asMap(value);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Не удалось прочитать рабочий OpenAPI-документ " + path, ex);
		}
	}

	private static Path resourcesOpenApiDirectory() {
		Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		List<Path> candidates = List.of(
				cwd.resolve("test-qwen-cli-app/src/main/resources/openapi"),
				cwd.resolve("src/main/resources/openapi")
		);
		for (Path candidate : candidates) {
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Каталог test-qwen-cli-app/src/main/resources/openapi не найден из " + cwd);
	}

	private static Path documentationOpenApiDirectory() {
		Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		List<Path> candidates = List.of(
				cwd.resolve("docs/external-service-gateway/openapi"),
				cwd.resolve("../docs/external-service-gateway/openapi").normalize()
		);
		for (Path candidate : candidates) {
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Зеркальный каталог docs/external-service-gateway/openapi не найден из " + cwd);
	}

	private static Map<String, Object> mapAt(Object root, String... keys) {
		Object current = root;
		for (String key : keys) {
			current = asMap(current).get(key);
			assertThat(current).as("YAML path segment %s", key).isNotNull();
		}
		return asMap(current);
	}

	private static Map<String, Object> asMap(Object value) {
		assertThat(value).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) value;
		return map;
	}

	private static Set<String> fieldNames(JsonNode node) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
	}
}
