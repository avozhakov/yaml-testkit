package com.yamltestkit.selftest.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Consumes the generic {@code blobSupplier} bean by its generic type. */
@Component
@RequiredArgsConstructor
public class BlobService {

    private final Supplier<Map<String, byte[]>> blobSupplier;

    public List<String> keys() {
        return blobSupplier.get().keySet().stream().sorted().toList();
    }
}
