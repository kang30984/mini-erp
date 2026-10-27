package io.github.kang30984.minierp.settlement;

import io.github.kang30984.minierp.auth.LoginUser;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settlements")
public class SettlementController {

    private final SettlementService service;

    public SettlementController(SettlementService service) {
        this.service = service;
    }

    @GetMapping("/{yyyymm}")
    public SettlementMonthResponse get(@PathVariable("yyyymm") String yyyymm) {
        return service.get(yyyymm);
    }

    @PostMapping("/{yyyymm}/preview")
    public SettlementMonthResponse preview(@AuthenticationPrincipal LoginUser loginUser,
                                           @PathVariable("yyyymm") String yyyymm) {
        return service.preview(loginUser.getUser(), yyyymm);
    }

    @PostMapping("/{yyyymm}/close")
    public SettlementMonthResponse close(@AuthenticationPrincipal LoginUser loginUser,
                                         @PathVariable("yyyymm") String yyyymm) {
        return service.close(loginUser.getUser(), yyyymm);
    }

    @GetMapping("/{yyyymm}/partners/{partnerId}")
    public List<SettlementDetail> details(@PathVariable("yyyymm") String yyyymm,
                                          @PathVariable("partnerId") long partnerId) {
        return service.details(yyyymm, partnerId);
    }

    @GetMapping("/{yyyymm}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable("yyyymm") String yyyymm) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("settlement-" + yyyymm + ".csv")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(service.csv(yyyymm));
    }
}
