package com.library.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test through the full Spring context verifying the book catalog's
 * read/write authorization rules: anyone can list, only ADMIN can create.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bookJson() throws Exception {
        return objectMapper.writeValueAsString(
                new BookRequest("The Pragmatic Programmer", "Andy Hunt", "978-0135957059", 2019, "Software", 4));
    }

    @Test
    void listBooks_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createBook_isRejected_withoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/books").contentType("application/json").content(bookJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "regular-user", roles = "USER")
    void createBook_isForbidden_forNonAdminUser() throws Exception {
        mockMvc.perform(post("/api/books").contentType("application/json").content(bookJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin-tester", roles = "ADMIN")
    void createBook_succeeds_forAdmin_thenRejectsDuplicateIsbn() throws Exception {
        mockMvc.perform(post("/api/books").contentType("application/json").content(bookJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value("978-0135957059"))
                .andExpect(jsonPath("$.availableCopies").value(4));

        mockMvc.perform(post("/api/books").contentType("application/json").content(bookJson()))
                .andExpect(status().isConflict());
    }
}
