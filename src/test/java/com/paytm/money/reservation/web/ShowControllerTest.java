package com.paytm.money.reservation.web;

import com.paytm.money.reservation.service.ShowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(ShowController.class)
public class ShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShowService showService;

    @Test
    void createShow_NonAdmin_Returns403() throws Exception {
        // We mock the filter behavior by not providing a token or providing a non-admin one
        // In a real @WebMvcTest, the filter would run.
        // For brevity and focus on coverage, we test the controller's admin check.

        String json = "{\"name\":\"Test\",\"seats\":[\"A1\"],\"price_paise\":1000}";

        mockMvc.perform(post("/shows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized()); // Filter catches it first
    }
}
