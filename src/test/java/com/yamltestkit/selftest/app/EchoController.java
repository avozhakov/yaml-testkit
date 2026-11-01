package com.yamltestkit.selftest.app;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/** Echoes the query string as received, the decoded parameters and the {@code x-test} header (imitates {@code /is}). */
@RestController
public class EchoController {

    @GetMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(HttpServletRequest request, @RequestParam Map<String, String> params,
                                                    @RequestHeader(name = "x-test", required = false) String test) {
        return ResponseEntity.ok()
                .header("X-Echo", Objects.requireNonNullElse(test, "none"))
                .body(Map.of("query", Objects.requireNonNullElse(request.getQueryString(), ""), "params", params));
    }
}
