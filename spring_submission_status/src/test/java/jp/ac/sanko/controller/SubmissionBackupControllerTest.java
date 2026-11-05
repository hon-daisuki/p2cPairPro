package jp.ac.sanko.controller;

import jp.ac.sanko.model.SubmissionStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubmissionBackupControllerTest {
    private final SubmissionStatusService service = new SubmissionStatusService(RestClient.builder(), new ObjectMapper());
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SubmissionStatusController(service)).build();

    @Test
    void seatsMatchPdfAndSeat18CannotLoginOrBeReactivated() throws Exception {
        var expected = java.util.List.of(
            java.util.List.of(12, 8, 2), java.util.List.of(36, 17, 23), java.util.List.of(10, 14, 6), java.util.List.of(5, 37, 7),
            java.util.List.of(39, 29, 28, 9), java.util.List.of(1, 20, 13, 16), java.util.List.of(30, 18, 21, 15), java.util.List.of(11, 40, 3, 22),
            java.util.List.of(33, 24, 19), java.util.List.of(4, 26, 27), java.util.List.of(25, 31, 35), java.util.List.of(32, 38, 34));
        mvc.perform(get("/")).andExpect(model().attribute("blocks", expected));
        assertEquals(40, expected.stream().flatMap(java.util.Collection::stream).distinct().count());
        mvc.perform(post("/login").param("attendanceNo", "18"))
            .andExpect(flash().attributeExists("error"))
            .andExpect(request().sessionAttributeDoesNotExist("attendanceNo"));
        mvc.perform(post("/admin/absence").param("targetNo", "18").sessionAttr("attendanceNo", 99))
            .andExpect(status().is3xxRedirection());
        assertTrue(service.allStatuses().get(18).absent());
    }

    @Test
    void onlyAdminCanExportAndImport() throws Exception {
        ReflectionTestUtils.setField(service, "sheetsApiUrl", "");
        ReflectionTestUtils.setField(service, "sheetsToken", "");
        service.submit(1);
        byte[] saved = service.exportBackup();
        service.submit(1);
        mvc.perform(get("/admin/backup")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/backup").sessionAttr("attendanceNo", 1)).andExpect(status().isForbidden());
        var file = new MockMultipartFile("backup", "saved.json", "application/json", saved);
        mvc.perform(multipart("/admin/restore").file(file).sessionAttr("attendanceNo", 1))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertEquals("作業③", service.allStatuses().get(1).currentTask());
        mvc.perform(get("/admin/backup").sessionAttr("attendanceNo", 99))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
        mvc.perform(multipart("/admin/restore").file(file).sessionAttr("attendanceNo", 99))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        assertEquals("作業②", service.allStatuses().get(1).currentTask());
    }
}
