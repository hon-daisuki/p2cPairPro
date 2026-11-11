package jp.ac.sanko.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import jp.ac.sanko.model.SubmissionStatusService;

@Controller
public class SubmissionStatusController {
    private static final List<List<Integer>> BLOCKS = List.of(
        List.of(20, 39, 38), List.of(1, 4, 28), List.of(14, 37, 7), List.of(36, 22, 33),
        List.of(31, 40, 32, 13), List.of(27, 35, 24, 2), List.of(8, 9, 26, 25), List.of(11, 23, 17, 34),
        List.of(29, 19, 0), List.of(30, 3, 16), List.of(10, 12, 5), List.of(6, 21, 15)
    );

    private final SubmissionStatusService service;

    public SubmissionStatusController(SubmissionStatusService service) {
        this.service = service;
    }

    @GetMapping({"/", "/status"})
    public String status(HttpSession session, Model model) {
        model.addAttribute("blocks", BLOCKS);
        model.addAttribute("statuses", service.allStatuses());
        model.addAttribute("attendanceNo", attendanceNo(session));
        return "submission-status";
    }

    @PostMapping("/login")
    public String login(@RequestParam int attendanceNo, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!service.canLogin(attendanceNo)) {
            redirectAttributes.addFlashAttribute("error", "入力した番号ではログインできません。欠席設定も確認してください。");
            return "redirect:/";
        }
        session.setAttribute("attendanceNo", attendanceNo);
        return "redirect:/";
    }

    @PostMapping("/submit")
    public String submit(HttpSession session, RedirectAttributes redirectAttributes) {
        Integer attendanceNo = attendanceNo(session);
        if (attendanceNo == null || attendanceNo == 99) {
            redirectAttributes.addFlashAttribute("error", "提出を記録するには出席番号でログインしてください。");
            return "redirect:/";
        }
        service.submit(attendanceNo);
        redirectAttributes.addFlashAttribute("message", "提出状況を更新しました。");
        return "redirect:/";
    }

    @PostMapping("/back")
    public String back(HttpSession session, RedirectAttributes redirectAttributes) {
        Integer attendanceNo = attendanceNo(session);
        if (attendanceNo == null || attendanceNo == 99) {
            redirectAttributes.addFlashAttribute("error", "提出状況を戻すには出席番号でログインしてください。");
            return "redirect:/";
        }
        service.back(attendanceNo);
        redirectAttributes.addFlashAttribute("message", "提出状況を1つ前に戻しました。");
        return "redirect:/";
    }

    @PostMapping("/admin/absence")
    public String toggleAbsence(HttpSession session,
            @RequestParam int targetNo, RedirectAttributes redirectAttributes) {
        Integer attendanceNo = attendanceNo(session);
        if (attendanceNo == null || attendanceNo != 99) {
            redirectAttributes.addFlashAttribute("error", "管理者のみ操作できます。");
            return "redirect:/";
        }
        service.toggleAbsent(targetNo);
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("attendanceNo");
        return "redirect:/";
    }

    @GetMapping("/admin/backup")
    public ResponseEntity<byte[]> backup(HttpSession session) throws java.io.IOException {
        if (!Integer.valueOf(99).equals(attendanceNo(session))) {
            return ResponseEntity.status(403).build();
        }
        String timestamp = LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"p2c-status-" + timestamp + ".json\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(service.exportBackup());
    }

    @PostMapping("/admin/restore")
    public String restore(@RequestParam("backup") MultipartFile backup, HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!Integer.valueOf(99).equals(attendanceNo(session))) {
            redirectAttributes.addFlashAttribute("error", "管理者のみ操作できます。");
            return "redirect:/";
        }
        if (backup.isEmpty() || backup.getSize() > 1024 * 1024) {
            redirectAttributes.addFlashAttribute("error", "1MB以下の保存ファイルを選択してください。");
            return "redirect:/";
        }
        try {
            service.importBackup(backup.getBytes());
            redirectAttributes.addFlashAttribute("message", "保存した提出状況と欠席設定を反映しました。");
        } catch (java.io.IOException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", "反映できませんでした。現在の課題の保存ファイルを確認してください。");
        }
        return "redirect:/";
    }

    private Integer attendanceNo(HttpSession session) {
        Object value = session.getAttribute("attendanceNo");
        return value instanceof Integer number ? number : null;
    }
}
