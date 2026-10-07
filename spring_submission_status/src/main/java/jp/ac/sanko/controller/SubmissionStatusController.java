package jp.ac.sanko.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jp.ac.sanko.model.SubmissionStatusService;

@Controller
public class SubmissionStatusController {
    private static final List<List<Integer>> BLOCKS = List.of(
        List.of(12, 8, 2), List.of(36, 17, 23), List.of(10, 14, 6), List.of(5, 37, 7),
        List.of(39, 29, 28, 9), List.of(1, 20, 13, 16), List.of(30, 18, 21, 15), List.of(11, 40, 3, 22),
        List.of(33, 24, 19), List.of(4, 26, 27), List.of(25, 31, 35), List.of(32, 38, 34)
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

    private Integer attendanceNo(HttpSession session) {
        Object value = session.getAttribute("attendanceNo");
        return value instanceof Integer number ? number : null;
    }
}
