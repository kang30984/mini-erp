package io.github.kang30984.minierp.master.rebate;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class RebatePolicyController {

    private final RebatePolicyService service;

    public RebatePolicyController(RebatePolicyService service) {
        this.service = service;
    }

    @GetMapping("/grades")
    public List<GradeResponse> grades() {
        return service.grades();
    }

    @GetMapping("/rebate-policies")
    public List<RebatePolicyResponse> policies(
            @RequestParam(name = "gradeCode", required = false) String gradeCode) {
        return service.policies(gradeCode);
    }

    @PostMapping("/rebate-policies")
    public ResponseEntity<List<RebatePolicyResponse>> register(@Valid @RequestBody RebatePolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }
}
