package org.viridexa.api.plant;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.viridexa.api.config.SecurityConfig;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlantController.class)
@Import(SecurityConfig.class)
class PlantControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PlantService plantService;

    @Test
    void allowsReadOnlyPlantSearchAndRejectsWrites() throws Exception {
        given(plantService.search(null, 3)).willReturn(new PlantSearchResult(null, 0, List.of()));

        mvc.perform(get("/api/v1/plants"))
                .andExpect(status().isOk());

        given(plantService.getById(1)).willReturn(new PlantRecord(
                1L, "苏铁", "Cycas revoluta Thunb.", null, "苏铁科", "Cycas", null, null,
                "REVIEW_PENDING", "VERIFIED_FROM_PRINTED_HEADING", "data/raw/course/课程设计资料.pdf",
                "11", "1", "669e9adbfbdfb855d196e5389b2b8aee763a226fee0ce7f0a7e83bc5bab73685"));
        mvc.perform(get("/api/v1/plants/1"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/plants"))
                .andExpect(status().isForbidden());
    }
}
