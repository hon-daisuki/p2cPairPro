package jp.ac.sanko.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SubmissionStatusService {
    private static final Logger logger = LoggerFactory.getLogger(SubmissionStatusService.class);
    private static final List<String> TASKS = List.of(
        "Lesson04_05", "Lesson04_06", "Lesson04_07", "SQL文提出", "リアぺ"
    );
    private final Map<Integer, Integer> submissionSteps = new HashMap<>();
    private final Set<Integer> absentNumbers = new HashSet<>();
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${p2c.sheets.api-url:}")
    private String sheetsApiUrl;

    @Value("${p2c.sheets.token:}")
    private String sheetsToken;

    public SubmissionStatusService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        for (int number = 1; number <= 40; number++) {
            this.submissionSteps.put(number, 0);
        }
    }

    @PostConstruct
    void restore() {
        if (!storageConfigured()) {
            logger.info("Google Spreadsheet storage is not configured. Using temporary in-memory state.");
            return;
        }
        try {
            JsonNode state = objectMapper.readTree(restClient.get().uri(sheetsApiUrl).retrieve().body(String.class));
            JsonNode storedSteps = state.path("steps");
            if (!storedSteps.isObject() || storedSteps.isEmpty()) {
                persist();
                logger.info("Initial submission status saved to Google Spreadsheet.");
                return;
            }
            submissionSteps.clear();
            absentNumbers.clear();
            storedSteps.fields().forEachRemaining(entry -> submissionSteps.put(Integer.valueOf(entry.getKey()), entry.getValue().asInt()));
            state.path("absentNumbers").forEach(number -> absentNumbers.add(number.asInt()));
            logger.info("Submission status restored from Google Spreadsheet.");
        } catch (Exception exception) {
            logger.warn("Could not restore submission status from Google Spreadsheet. Starting with empty state.", exception);
        }
    }

    public synchronized Map<Integer, SeatStatus> allStatuses() {
        Map<Integer, SeatStatus> statuses = new HashMap<>();
        for (int number = 1; number <= 40; number++) {
            statuses.put(number, statusOf(number));
        }
        return statuses;
    }

    public synchronized boolean canLogin(int attendanceNo) {
        return attendanceNo == 99 || (attendanceNo >= 1 && attendanceNo <= 40 && !absentNumbers.contains(attendanceNo));
    }

    public synchronized void submit(int attendanceNo) {
        if (!canLogin(attendanceNo) || attendanceNo == 99) {
            return;
        }
        submissionSteps.put(attendanceNo, Math.min(TASKS.size(), stepOf(attendanceNo) + 1));
        persist();
    }

    public synchronized void back(int attendanceNo) {
        if (!canLogin(attendanceNo) || attendanceNo == 99) {
            return;
        }
        submissionSteps.put(attendanceNo, Math.max(0, stepOf(attendanceNo) - 1));
        persist();
    }

    public synchronized void toggleAbsent(int attendanceNo) {
        if (absentNumbers.contains(attendanceNo)) {
            absentNumbers.remove(attendanceNo);
        } else {
            absentNumbers.add(attendanceNo);
        }
        persist();
    }

    private SeatStatus statusOf(int attendanceNo) {
        if (absentNumbers.contains(attendanceNo)) {
            return new SeatStatus(true, List.of(), "欠席", "absent");
        }
        int step = stepOf(attendanceNo);
        List<String> completed = new ArrayList<>(TASKS.subList(0, step));
        if (step == TASKS.size()) {
            return new SeatStatus(false, completed, "free!", "stage-free");
        }
        return new SeatStatus(false, completed, TASKS.get(step), "stage-" + step);
    }

    private int stepOf(int attendanceNo) {
        return Math.max(0, Math.min(TASKS.size(), submissionSteps.getOrDefault(attendanceNo, 0)));
    }

    private boolean storageConfigured() {
        return !sheetsApiUrl.isBlank() && !sheetsToken.isBlank();
    }

    private void persist() {
        if (!storageConfigured()) {
            return;
        }
        try {
            Map<String, Object> state = Map.of(
                "token", sheetsToken,
                "steps", new HashMap<>(submissionSteps),
                "absentNumbers", new ArrayList<>(absentNumbers)
            );
            restClient.post().uri(sheetsApiUrl).contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(state)).retrieve().toBodilessEntity();
        } catch (Exception exception) {
            logger.warn("Could not save submission status to Google Spreadsheet.", exception);
        }
    }
}
