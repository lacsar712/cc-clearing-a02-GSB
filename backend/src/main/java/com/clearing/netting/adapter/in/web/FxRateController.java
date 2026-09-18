package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.FxRateApplicationService;
import com.clearing.netting.domain.model.FxRate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    private final FxRateApplicationService rateService;

    public FxRateController(FxRateApplicationService rateService) {
        this.rateService = rateService;
    }

    @GetMapping
    public List<FxRateResponse> list() {
        AuthContext.require();
        return rateService.list().stream().map(FxRateResponse::from).collect(Collectors.toList());
    }

    @PostMapping
    public FxRateResponse create(@Valid @RequestBody CreateFxRateRequest request) {
        AuthContext.requireOperator();
        return FxRateResponse.from(rateService.create(
                request.baseCurrency(),
                request.quoteCurrency(),
                request.rate(),
                request.effectiveDate()));
    }

    @PutMapping("/{id}")
    public FxRateResponse update(@PathVariable("id") String id,
                                 @Valid @RequestBody UpdateFxRateRequest request) {
        AuthContext.requireOperator();
        return FxRateResponse.from(rateService.update(id, request.rate(), request.effectiveDate()));
    }

    public record CreateFxRateRequest(
            @NotBlank String baseCurrency,
            @NotBlank String quoteCurrency,
            @NotNull @DecimalMin(value = "0.00000001", message = "rate must be positive") BigDecimal rate,
            @NotNull LocalDate effectiveDate) {
    }

    public record UpdateFxRateRequest(
            @NotNull @DecimalMin(value = "0.00000001", message = "rate must be positive") BigDecimal rate,
            @NotNull LocalDate effectiveDate) {
    }

    public record FxRateResponse(
            String rateId,
            String baseCurrency,
            String quoteCurrency,
            String pair,
            BigDecimal rate,
            LocalDate effectiveDate) {
        static FxRateResponse from(FxRate r) {
            return new FxRateResponse(
                    r.getRateId(),
                    r.getBaseCurrency(),
                    r.getQuoteCurrency(),
                    r.getBaseCurrency() + "/" + r.getQuoteCurrency(),
                    r.getRate(),
                    r.getEffectiveDate());
        }
    }
}
