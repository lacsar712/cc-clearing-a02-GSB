package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.FxReportApplicationService;
import com.clearing.netting.domain.model.ConvertedNetLine;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/fx-reports")
public class FxReportController {

    private final FxReportApplicationService fxReportService;

    public FxReportController(FxReportApplicationService fxReportService) {
        this.fxReportService = fxReportService;
    }

    @GetMapping("/converted-net")
    public ConvertedNetResponse convertedNet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate settleDate,
            @RequestParam(required = false) String targetCurrency) {
        AuthContext.require();
        FxReportApplicationService.ConvertedNetReport report =
                fxReportService.convertedNet(settleDate, targetCurrency);
        return new ConvertedNetResponse(
                report.settleDate(),
                report.targetCurrency(),
                report.lines().stream().map(ConvertedLineResponse::from).toList(),
                report.memberTotals().stream()
                        .map(t -> new MemberTotalResponse(t.memberId(), t.totalConverted()))
                        .toList(),
                report.grandTotal());
    }

    public record ConvertedLineResponse(
            String memberId,
            String currency,
            BigDecimal netAmount,
            BigDecimal rate,
            BigDecimal convertedAmount) {
        static ConvertedLineResponse from(ConvertedNetLine l) {
            return new ConvertedLineResponse(l.memberId(), l.currency(), l.netAmount(), l.rate(), l.convertedAmount());
        }
    }

    public record MemberTotalResponse(String memberId, BigDecimal totalConverted) {
    }

    public record ConvertedNetResponse(
            LocalDate settleDate,
            String targetCurrency,
            List<ConvertedLineResponse> lines,
            List<MemberTotalResponse> memberTotals,
            BigDecimal grandTotal) {
    }
}
