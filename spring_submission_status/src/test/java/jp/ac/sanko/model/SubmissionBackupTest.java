package jp.ac.sanko.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionBackupTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private SubmissionStatusService service() {
        SubmissionStatusService service = new SubmissionStatusService(RestClient.builder(), mapper);
        ReflectionTestUtils.setField(service, "sheetsApiUrl", "");
        ReflectionTestUtils.setField(service, "sheetsToken", "");
        return service;
    }

    @Test
    void restoresAllSeatsIncludingProgressHiddenByAbsence() throws Exception {
        SubmissionStatusService source = service();
        for (int number = 1; number <= 40; number++) {
            for (int step = 0; step < number % 5; step++) source.submit(number);
        }
        source.toggleAbsent(2);
        byte[] backup = source.exportBackup();
        assertFalse(new String(backup, java.nio.charset.StandardCharsets.UTF_8).contains("token"));
        SubmissionStatusService target = service();
        target.importBackup(backup);
        assertEquals(source.allStatuses(), target.allStatuses());
        assertFalse(target.canLogin(2));
        target.toggleAbsent(2);
        assertEquals("確認試験", target.allStatuses().get(2).currentTask());
    }

    @Test
    void rejectsInvalidAndDifferentWorkflowsWithoutPartialChanges() throws Exception {
        SubmissionStatusService service = service();
        service.submit(1);
        var before = service.allStatuses();
        ObjectNode backup = (ObjectNode) mapper.readTree(service.exportBackup());
        ((ObjectNode) backup.path("steps")).put("40", 999);
        assertThrows(IllegalArgumentException.class, () -> service.importBackup(mapper.writeValueAsBytes(backup)));
        assertEquals(before, service.allStatuses());
        backup.set("tasks", mapper.valueToTree(java.util.List.of("old task")));
        assertThrows(IllegalArgumentException.class, () -> service.importBackup(mapper.writeValueAsBytes(backup)));
        assertEquals(before, service.allStatuses());
        assertThrows(java.io.IOException.class, () -> service.importBackup("not json".getBytes()));
        assertEquals(before, service.allStatuses());
    }
}
