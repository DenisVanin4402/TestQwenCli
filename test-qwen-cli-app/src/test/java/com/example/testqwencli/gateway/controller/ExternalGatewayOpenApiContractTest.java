package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.gateway.support.GatewayTestRequests;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
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

	private static final List<String> DASHBOARD_API_PATHS = List.of(
			"/dashboard/api/snapshot",
			"/dashboard/api/health",
			"/dashboard/api/load/profile",
			"/dashboard/api/load/start",
			"/dashboard/api/load/stop",
			"/dashboard/api/load/reset",
			"/dashboard/api/upstream-simulation"
	);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void generatedOpenApiMatchesDocumentedGatewayContract() throws Exception {
		JsonNode generated = generatedOpenApi();
		Map<String, Object> syncDocument = loadOpenApiDocument("external-gateway-sync.yaml");
		Map<String, Object> asyncDocument = loadOpenApiDocument("external-gateway-async.yaml");

		assertThat(generated.path("info").path("title").asText())
				.isEqualTo("TestQwenCli External Service Gateway API");

		assertOperationMatchesDocument(generated, syncDocument, "/v1/external/sync", "post");
		assertOperationMatchesDocument(generated, asyncDocument, "/v1/external/async", "post");
		assertOperationMatchesDocument(generated, asyncDocument, "/v1/external/async/{taskId}", "get");
		assertOperationMatchesDocument(generated, asyncDocument, "/v1/external/async/{taskId}", "delete");
		assertOperationMatchesDocument(generated, asyncDocument, "/v1/external/async/by-external-id/{externalId}",
				"get");
		assertOperationMatchesDocument(generated, asyncDocument, "/v1/external/async/{taskId}/retry", "post");

		assertDocumentedSchemaFieldsExist(generated, syncDocument, "ExternalSyncRequest");
		assertDocumentedSchemaFieldsExist(generated, syncDocument, "ExternalSyncResponse");
		assertDocumentedSchemaFieldsExist(generated, syncDocument, "ErrorResponse");
		assertDocumentedSchemaFieldsExist(generated, asyncDocument, "ExternalAsyncRequest");
		assertDocumentedSchemaFieldsExist(generated, asyncDocument, "AsyncSubmitResponse");
		assertDocumentedSchemaFieldsExist(generated, asyncDocument, "AsyncTask");
		assertDocumentedSchemaFieldsExist(generated, asyncDocument, "TaskError");

		assertThat(fieldNames(generated.path("paths"))).containsAll(DASHBOARD_API_PATHS);
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

	private JsonNode generatedOpenApi() throws Exception {
		MvcResult result = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private void assertOperationMatchesDocument(
			JsonNode generated,
			Map<String, Object> document,
			String path,
			String method
	) {
		Map<String, Object> documentedOperation = mapAt(document, "paths", path, method);
		JsonNode generatedOperation = generated.path("paths").path(path).path(method);

		assertThat(generatedOperation.isMissingNode())
				.as("%s %s опубликован в generated OpenAPI", method.toUpperCase(), path)
				.isFalse();
		assertThat(generatedOperation.path("operationId").asText())
				.isEqualTo(documentedOperation.get("operationId"));

		assertParametersMatchDocument(generatedOperation, document, documentedOperation);
		Map<String, Object> documentedResponses = mapAt(documentedOperation, "responses");
		assertThat(fieldNames(generatedOperation.path("responses"))).containsAll(documentedResponses.keySet());
		documentedResponses.keySet().forEach(responseCode -> assertResponseSchemaMatchesDocument(
				generatedOperation, document, documentedOperation, responseCode));
	}

	private void assertParametersMatchDocument(
			JsonNode generatedOperation,
			Map<String, Object> document,
			Map<String, Object> documentedOperation
	) {
		Object parameters = documentedOperation.get("parameters");
		if (parameters == null) {
			return;
		}
		for (Object rawParameter : asList(parameters)) {
			Map<String, Object> expected = resolveParameter(document, rawParameter);
			JsonNode actual = findGeneratedParameter(generatedOperation, (String) expected.get("name"),
					(String) expected.get("in"));

			assertThat(actual.isMissingNode())
					.as("parameter %s in %s опубликован", expected.get("name"), expected.get("in"))
					.isFalse();
			assertSchemaAttributeMatches(actual.path("schema"), expected, "type");
			assertSchemaAttributeMatches(actual.path("schema"), expected, "format");
			assertSchemaAttributeMatches(actual.path("schema"), expected, "minimum");
			assertSchemaAttributeMatches(actual.path("schema"), expected, "minLength");
			assertSchemaAttributeMatches(actual.path("schema"), expected, "maxLength");
		}
	}

	private void assertResponseSchemaMatchesDocument(
			JsonNode generatedOperation,
			Map<String, Object> document,
			Map<String, Object> documentedOperation,
			String responseCode
	) {
		String expectedSchema = documentedResponseSchemaName(document, documentedOperation, responseCode);
		if (expectedSchema == null) {
			return;
		}

		JsonNode generatedSchema = generatedOperation.path("responses")
				.path(responseCode)
				.path("content");
		assertThat(refName(firstResponseSchemaRef(generatedSchema)))
				.as("schema для response %s", responseCode)
				.isEqualTo(expectedSchema);
	}

	private void assertDocumentedSchemaFieldsExist(
			JsonNode generated,
			Map<String, Object> document,
			String schemaName
	) {
		JsonNode generatedSchema = generated.path("components").path("schemas").path(schemaName);
		assertThat(generatedSchema.isMissingNode())
				.as("schema %s опубликована в generated OpenAPI", schemaName)
				.isFalse();
		Set<String> generatedProperties = fieldNames(generatedSchema.path("properties"));

		assertThat(generatedProperties)
				.as("properties schema %s", schemaName)
				.containsAll(documentedSchemaProperties(document, schemaName));
		assertThat(generatedProperties)
				.as("required fields schema %s", schemaName)
				.containsAll(documentedRequiredFields(document, schemaName));
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

	private static String documentedResponseSchemaName(
			Map<String, Object> document,
			Map<String, Object> operation,
			String responseCode
	) {
		Map<String, Object> response = mapAt(operation, "responses", responseCode);
		Object responseRef = response.get("$ref");
		if (responseRef instanceof String ref) {
			response = resolveRef(document, ref);
		}

		Object content = response.get("content");
		if (content == null) {
			return null;
		}
		Map<String, Object> schema = mapAt(response, "content", "application/json", "schema");
		Object schemaRef = schema.get("$ref");
		if (!(schemaRef instanceof String ref)) {
			return null;
		}
		return refName(ref);
	}

	private static Map<String, Object> resolveRef(Map<String, Object> document, String ref) {
		assertThat(ref).startsWith("#/");
		String[] segments = ref.substring(2).split("/");
		return mapAt(document, segments);
	}

	private static Map<String, Object> resolveParameter(Map<String, Object> document, Object rawParameter) {
		Map<String, Object> parameter = asMap(rawParameter);
		Object ref = parameter.get("$ref");
		if (ref instanceof String refValue) {
			return resolveRef(document, refValue);
		}
		return parameter;
	}

	private static JsonNode findGeneratedParameter(JsonNode generatedOperation, String name, String in) {
		for (JsonNode parameter : generatedOperation.path("parameters")) {
			if (name.equals(parameter.path("name").asText()) && in.equals(parameter.path("in").asText())) {
				return parameter;
			}
		}
		return MissingNode.getInstance();
	}

	private static void assertSchemaAttributeMatches(JsonNode actualSchema, Map<String, Object> expectedParameter,
			String attribute) {
		Object schema = expectedParameter.get("schema");
		if (schema == null) {
			return;
		}
		Object expectedValue = asMap(schema).get(attribute);
		if (expectedValue == null) {
			return;
		}
		assertThat(actualSchema.path(attribute).asText())
				.as("schema attribute %s для parameter %s", attribute, expectedParameter.get("name"))
				.isEqualTo(expectedValue.toString());
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

	private static List<Object> asList(Object value) {
		assertThat(value).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<Object> list = (List<Object>) value;
		return list;
	}

	private static Set<String> fieldNames(JsonNode node) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private static String firstResponseSchemaRef(JsonNode contentNode) {
		JsonNode jsonSchema = contentNode.path("application/json").path("schema").path("$ref");
		if (!jsonSchema.isMissingNode()) {
			return jsonSchema.asText();
		}
		var fields = contentNode.fields();
		assertThat(fields.hasNext()).as("response content опубликован").isTrue();
		return fields.next().getValue().path("schema").path("$ref").asText();
	}

	private static String refName(String ref) {
		assertThat(ref).startsWith("#/components/schemas/");
		return ref.substring(ref.lastIndexOf('/') + 1);
	}
}
