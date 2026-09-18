package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.FxReportApplicationService;
import com.clearing.netting.application.FxReportApplicationService.CurrencyTotal;
import com.clearing.netting.application.FxReportApplicationService.FxReport;
import com.clearing.netting.application.FxReportApplicationService.FxReportLine;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/fx-reports")
public class FxReportController {

    private final FxReportApplicationService reportService;

    public FxReportController(FxReportApplicationService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/converted-net")
    public ConvertedNetReportResponse convertedNet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate settleDate,
            @RequestParam String targetCurrency) {
        AuthContext.require();
        FxReport report = reportService.build(settleDate, targetCurrency);
        return ConvertedNetReportResponse.from(report);
    }

    public record ConvertedNetReportResponse(
            LocalDate settleDate,
            String targetCurrency,
            int runCount,
            java.util.List<LineResponse> lines,
            java.util.List<TotalResponse> currencyTotals,
            java.math.BigDecimal grandTotalConverted) {

        static ConvertedNetReportResponse from(FxReport r) {
            return new ConvertedNetReportResponse(
                    r.settleDate(),
                    r.targetCurrency(),
                    r.runCount(),
                    r.lines().stream().map(LineResponse::from).toList(),
                    r.currencyTotals().stream().map(TotalResponse::from).toList(),
                    r.grandTotalConverted());
        }
    }

    public record LineResponse(
            String memberId,
            String memberName,
            String sourceCurrency,
            java.math.BigDecimal netAmount,
            String targetCurrency,
            java.math.BigDecimal convertedAmount,
            java.math.BigDecimal rateFactor,
            String rateDirection,
            LocalDate rateEffectiveDate,
            String rateId) {
        static LineResponse from(FxReportLine l) {
            return new LineResponse(
                    l.memberId(),
                    l.memberName(),
                    l.sourceCurrency(),
                    l.netAmount(),
                    l.targetCurrency(),
                    l.convertedAmount(),
                    l.rateFactor(),
                    l.rateDirection(),
                    l.rateEffectiveDate(),
                    l.rateId());
        }
    }

    public record TotalResponse(String currency, java.math.BigDecimal netAmount) {
        static TotalResponse from(CurrencyTotal t) {
            return new TotalResponse(t.currency(), t.netAmount());
        }
    }
}
