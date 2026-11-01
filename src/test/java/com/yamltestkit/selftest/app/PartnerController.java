package com.yamltestkit.selftest.app;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerClient partnerClient;

    @GetMapping(path = "/partner/items/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String item(@PathVariable String id) {
        return partnerClient.item(id);
    }
}
