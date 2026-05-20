package com.example.testqwencli;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для TaskController.
 * Покрывают все acceptance criteria (AC-1..AC-14).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ===== POST /api/v1/tasks =====

    /** AC-1: POST с валидным телом → 201, UUID, status TODO, createdAt/updatedAt */
    @Test
    void createTask_returns201_withUUID_andStatusTODO() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Купить молоко"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Купить молоко"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    /** AC-1 (доп): POST с description → 201 и description присутствует */
    @Test
    void createTask_withDescription_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Buy milk", "description": "from the store"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("from the store"));
    }

    /** AC-1 (доп): POST с optional status → 201 и заданный статус */
    @Test
    void createTask_withStatus_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Urgent", "status": "in_progress"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    /** AC-2: POST без title → 400 */
    @Test
    void createTask_withoutTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "без заголовка"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /** AC-2 (доп): POST с пустым title → 400 */
    @Test
    void createTask_withEmptyTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** AC-3: POST с title > 255 символов → 400 */
    @Test
    void createTask_withTitleOver255Chars_returns400() throws Exception {
        String longTitle = "a".repeat(256);
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** AC-4: POST с неизвестным status → 400 */
    @Test
    void createTask_withUnknownStatus_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Test", "status": "UNKNOWN_STATUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ===== GET /api/v1/tasks =====

    /** AC-5: GET list → 200, пустой массив */
    @Test
    void listTasks_emptyReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    /** AC-5 (доп): GET list с задачами → 200 + массив */
    @Test
    void listTasks_withTasks_returnsArray() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Task 1"}
                        """));
        mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Task 2"}
                        """));

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].title").value(hasItems("Task 1", "Task 2")));
    }

    // ===== GET /api/v1/tasks/{id} =====

    /** AC-6: GET by ID → 200 */
    @Test
    void getTaskById_existingTask_returns200() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Get me"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.parse(body).read("$.id");

        mockMvc.perform(get("/api/v1/tasks/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Get me"));
    }

    /** AC-7: GET by non-existent ID → 404 */
    @Test
    void getTaskById_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /** AC-8: PUT → 200, createdAt неизменен */
    @Test
    void updateTask_existingTask_returns200_withUnchangedCreatedAt() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Old", "description": "desc", "status": "todo"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.parse(createResponse).read("$.id");
        String originalCreatedAt = com.jayway.jsonpath.JsonPath.parse(createResponse).read("$.createdAt");

        Thread.sleep(100); // гарантировать, что updatedAt будет отличаться

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New", "description": "new desc", "status": "in_progress"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.description").value("new desc"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.createdAt").value(originalCreatedAt));
    }

    /** AC-9: PUT по не-существующему ID → 404 */
    @Test
    void updateTask_nonExistent_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/tasks/00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New", "description": "desc", "status": "done"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(404));
    }

    /** AC-8 (доп): PUT с пустым title → 400 */
    @Test
    void updateTask_withEmptyTitle_returns400() throws Exception {
        String createBody = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "To be updated"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.parse(createBody).read("$.id");

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "description": "", "status": "done"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** AC-8 (доп): PUT с неизвестным status → 400 */
    @Test
    void updateTask_withUnknownStatus_returns400() throws Exception {
        String createBody = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "To be updated"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.parse(createBody).read("$.id");

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated", "description": "", "status": "FAKE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** AC-8 (доп): PUT без status → 400 */
    @Test
    void updateTask_withoutStatus_returns400() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "To be updated"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.parse(body).read("$.id");

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated", "description": "desc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ===== DELETE /api/v1/tasks/{id} =====

    /** AC-10: DELETE существующей задачи → 200, тело с id/title/status */
    @Test
    void deleteTask_existingTask_returns200_withBody() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Delete me"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.parse(body).read("$.id");

        mockMvc.perform(delete("/api/v1/tasks/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Delete me"))
                .andExpect(jsonPath("$.status").exists());
    }

    /** AC-10 (доп): Удалённая задача больше не доступна по GET */
    @Test
    void deleteTask_isGoneAfterDelete() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Gone"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.parse(body).read("$.id");

        mockMvc.perform(delete("/api/v1/tasks/" + id))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/" + id))
                .andExpect(status().isNotFound());
    }

    /** AC-11: DELETE non-existent ID → 404 */
    @Test
    void deleteTask_nonExistent_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ===== AC-12: non-UUID in path → 400 =====

    @Test
    void getTaskById_nonUUID_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateTask_nonUUID_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/tasks/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "X", "description": "", "status": "todo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deleteTask_nonUUID_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ===== AC-13: Content-Type application/json на всех ответах =====

    @Test
    void allResponsesHaveJsonContentType() throws Exception {
        // POST 201
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "CT check"}
                                """))
                .andExpect(header().string("Content-Type", containsString("application/json")));

        // GET list 200
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(header().string("Content-Type", containsString("application/json")));

        // POST 400
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": ""}
                                """))
                .andExpect(header().string("Content-Type", containsString("application/json")));

        // GET 404
        mockMvc.perform(get("/api/v1/tasks/00000000-0000-0000-0000-000000000000"))
                .andExpect(header().string("Content-Type", containsString("application/json")));

        // GET non-UUID 400
        mockMvc.perform(get("/api/v1/tasks/not-a-uuid"))
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }
}
