package dev.worktime.timer;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.worktime.timer.TimerDtos.EntryResponse;
import dev.worktime.timer.TimerDtos.StartTimerRequest;

@RestController
@RequestMapping("/api/employees/{employeeId}/timer")
public class TimerController {

    private final TimerService service;

    public TimerController(TimerService service) {
        this.service = service;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse start(@PathVariable long employeeId, @Valid @RequestBody StartTimerRequest request) {
        return service.start(employeeId, request.description());
    }

    @PostMapping("/stop")
    public EntryResponse stop(@PathVariable long employeeId) {
        return service.stop(employeeId);
    }
}
