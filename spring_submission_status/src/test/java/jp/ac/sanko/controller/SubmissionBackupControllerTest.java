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
        assertEquals("確認試験", service.allStatuses().get(1).currentTask());
        mvc.perform(get("/admin/backup").sessionAttr("attendanceNo", 99))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
        mvc.perform(multipart("/admin/restore").file(file).sessionAttr("attendanceNo", 99))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        assertEquals("JPQL2", service.allStatuses().get(1).currentTask());
    }
}
