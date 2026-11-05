package com.capaciti.ops.report;

import com.capaciti.ops.report.ReportDtos.WeeklyReport;
import com.capaciti.ops.ticket.Category;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) { this.service = service; }

    /** JSON version - lets the frontend render the report on screen. */
    @GetMapping("/weekly")
    public WeeklyReport weekly(@RequestParam(required = false) Category department) {
        return service.weekly(department);
    }

    /** Downloadable PDF version. */
    @GetMapping("/weekly/pdf")
    public ResponseEntity<byte[]> weeklyPdf(@RequestParam(required = false) Category department) {
        byte[] bytes = service.weeklyPdf(department);
        String name = "weekly-report-" + (department == null ? "all" : department.name().toLowerCase()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build().toString())
                .body(bytes);
    }
}
