package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.FxRateApplicationService;
import com.clearing.netting.domain.model.FxRate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fx-rates")
public class FxRateController {

    private final FxRateApplicationService fxRateService;

    public FxRateController(FxRateApplicationService fxRateService) {
        this.fxRateService = fxRateService;
    }

    @GetMapping
    public List<FxRateResponse> list() {
        AuthContext.require();
        return fxRateService.list().stream().map(FxRateResponse::from).collect(Collectors.toList());
    }

    @PostMapping
    public FxRateResponse upsert(@Valid @RequestBody UpsertRateRequest request) {
        AuthContext.requireOperator();
        return FxRateResponse.from(fxRateService.upsert(
                request.baseCurrency(), request.quoteCurrency(), request.rate(), request.effectiveDate()));
    }

    public record UpsertRateRequest(
            @NotBlank String baseCurrency,
            @NotBlank String quoteCurrency,
            @NotNull @DecimalMin("0.0000000001") BigDecimal rate,
            @NotNull LocalDate effectiveDate) {
    }

    public record FxRateResponse(
            String rateId,
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            LocalDate effectiveDate) {
        static FxRateResponse from(FxRate r) {
            return new FxRateResponse(
                    r.getRateId(), r.getBaseCurrency(), r.getQuoteCurrency(), r.getRate(), r.getEffectiveDate());
        }
    }
}
