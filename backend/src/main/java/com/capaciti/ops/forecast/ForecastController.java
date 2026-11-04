package com.capaciti.ops.forecast;

import com.capaciti.ops.forecast.ForecastDtos.Forecast;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {

    private final ForecastService service;

    public ForecastController(ForecastService service) { this.service = service; }

    @GetMapping
    public Forecast forecast(@RequestParam(defaultValue = "7") int horizonDays) {
        return service.forecast(horizonDays);
    }
}
