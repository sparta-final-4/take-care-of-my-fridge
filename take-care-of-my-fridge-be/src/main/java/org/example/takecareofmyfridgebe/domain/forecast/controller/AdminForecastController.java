package org.example.takecareofmyfridgebe.domain.forecast.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.forecast.dto.ForecastResponse;
import org.example.takecareofmyfridgebe.domain.forecast.service.ForecastReadService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/forecasts")
@RequiredArgsConstructor
public class AdminForecastController {

    private final ForecastReadService forecastReadService;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<ForecastResponse>> getLatest() {
        return ResponseEntity.ok(ApiResponse.ok(forecastReadService.getLatest()));
    }

    @GetMapping("/{weekStart}")
    public ResponseEntity<ApiResponse<ForecastResponse>> getByWeek(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ResponseEntity.ok(ApiResponse.ok(forecastReadService.getByWeek(weekStart)));
    }
}
