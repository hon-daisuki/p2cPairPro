package jp.ac.sanko.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class SubmissionStatusService {
    private static final List<String> TASKS = List.of("pair06-1", "pair06-2", "portfolio04");
    private final Map<Integer, Integer> submissionSteps = new HashMap<>();
    private final Set<Integer> absentNumbers = new HashSet<>();

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
    }

    public synchronized void back(int attendanceNo) {
        if (!canLogin(attendanceNo) || attendanceNo == 99) {
            return;
        }
        submissionSteps.put(attendanceNo, Math.max(0, stepOf(attendanceNo) - 1));
    }

    public synchronized void toggleAbsent(int attendanceNo) {
        if (absentNumbers.contains(attendanceNo)) {
            absentNumbers.remove(attendanceNo);
        } else {
            absentNumbers.add(attendanceNo);
        }
    }

    private SeatStatus statusOf(int attendanceNo) {
        if (absentNumbers.contains(attendanceNo)) {
            return new SeatStatus(true, List.of(), "欠席", "absent");
        }
        int step = stepOf(attendanceNo);
        List<String> completed = new ArrayList<>(TASKS.subList(0, step));
        if (step == TASKS.size()) {
            return new SeatStatus(false, completed, "Free!", "stage-free");
        }
        return new SeatStatus(false, completed, TASKS.get(step), "stage-" + step);
    }

    private int stepOf(int attendanceNo) {
        return Math.max(0, Math.min(TASKS.size(), submissionSteps.getOrDefault(attendanceNo, 0)));
    }
}
