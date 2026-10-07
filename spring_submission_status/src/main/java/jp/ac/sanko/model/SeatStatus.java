package jp.ac.sanko.model;

import java.util.List;

public record SeatStatus(boolean absent, List<String> completedTasks, String currentTask, String colorClass) {
}
