package org.viridexa.api.science;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viridexa.api.config.SecurityConfig;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScientificController.class)
@Import(SecurityConfig.class)
class ScientificControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ScientificService service;
    @Test void publicFactsRead() throws Exception {
        given(service.facts(null,30)).willReturn(List.of());
        mvc.perform(get("/api/v1/science/facts")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void lineageRead() throws Exception {
        given(service.lineage("absent")).willReturn(List.of());
        mvc.perform(get("/api/v1/science/lineage/absent")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void plantSearchPassesQueryAndLimit() throws Exception {
        given(service.plantSearch("rosa",12)).willReturn(List.of());
        mvc.perform(get("/api/v1/science/plants").param("q","rosa").param("limit","12"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void measurementsPassesFilters() throws Exception {
        given(service.measurements("marker", "sample-1", "dry", "source-1", 8)).willReturn(List.of());
        mvc.perform(get("/api/v1/science/measurements").param("kind","marker").param("sampleId","sample-1")
                .param("basis","dry").param("sourceId","source-1").param("limit","8"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void coverageIsPublic() throws Exception {
        given(service.coverage()).willReturn(List.of());
        mvc.perform(get("/api/v1/science/coverage")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void rejectsWrite() throws Exception {
        mvc.perform(post("/api/v1/science/facts")).andExpect(status().isForbidden());
    }
    @Test void deniesUnauthenticatedAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/science/candidates")).andExpect(status().isUnauthorized());
    }
}
