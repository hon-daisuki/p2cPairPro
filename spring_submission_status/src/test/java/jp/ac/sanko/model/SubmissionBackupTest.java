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
    void seat18RemainsAbsentAfterToggleAndBackupImport() throws Exception {
        SubmissionStatusService service = service();
        assertTrue(service.allStatuses().get(18).absent());
        assertFalse(service.canLogin(18));
        service.toggleAbsent(18);
        service.submit(18);
        service.back(18);
        assertTrue(service.allStatuses().get(18).absent());
        ObjectNode backup = (ObjectNode) mapper.readTree(service.exportBackup());
        backup.set("absentNumbers", mapper.createArrayNode());
        service.importBackup(mapper.writeValueAsBytes(backup));
        assertTrue(service.allStatuses().get(18).absent());
        assertFalse(service.canLogin(18));
        assertTrue(mapper.readTree(service.exportBackup()).path("absentNumbers").toString().contains("18"));
        service.toggleAbsent(17);
        assertFalse(service.canLogin(17));
        service.toggleAbsent(17);
        assertTrue(service.canLogin(17));
    }

    @Test
    void spreadsheetRestoreCannotReactivateSeat18() {
        var builder = RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var service = new SubmissionStatusService(builder, mapper);
        ReflectionTestUtils.setField(service, "sheetsApiUrl", "https://example.test/state");
        ReflectionTestUtils.setField(service, "sheetsToken", "test");
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://example.test/state"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                "{\"steps\":{\"18\":5},\"absentNumbers\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));
        service.restore();
        assertTrue(service.allStatuses().get(18).absent());
        assertFalse(service.canLogin(18));
        server.verify();
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
        assertEquals("作業③", target.allStatuses().get(2).currentTask());
    }

    @Test
    void progressesThroughEveryPair08TaskAndBackWithoutChangingOtherSeats() {
        SubmissionStatusService service = service();
        var tasks = java.util.List.of("作業①", "作業②", "作業③", "作業④", "作業⑤", "作業⑥",
            "作業⑦", "作業⑧", "作業⑨", "pair08提出", "リアぺ", "free!");
        for (int step = 0; step < tasks.size(); step++) {
            var seat = service.allStatuses().get(1);
            assertEquals(tasks.get(step), seat.currentTask());
            assertEquals(step == tasks.size() - 1 ? "stage-free" : "stage-" + step, seat.colorClass());
            assertEquals("作業①", service.allStatuses().get(2).currentTask());
            service.submit(1);
        }
        assertEquals("free!", service.allStatuses().get(1).currentTask());
        for (int step = tasks.size() - 2; step >= 0; step--) {
            service.back(1);
            assertEquals(tasks.get(step), service.allStatuses().get(1).currentTask());
        }
        service.back(1);
        assertEquals("作業①", service.allStatuses().get(1).currentTask());
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
