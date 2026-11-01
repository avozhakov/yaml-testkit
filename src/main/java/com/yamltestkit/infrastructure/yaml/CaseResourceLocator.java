package com.yamltestkit.infrastructure.yaml;

import com.yamltestkit.domain.model.CaseSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@code @YamlCases} globs to case files on the classpath, sorted by path relative to the glob root.
 * Globs are relative to the classpath root and use Ant syntax; a glob that does not end in {@code .yaml} selects
 * every {@code *.yaml} file below it. Files whose name starts with {@code _} are configuration and are skipped.
 */
public final class CaseResourceLocator {

    private static final String YAML_SUFFIX = ".yaml";

    private final ResourcePatternResolver resolver;

    public CaseResourceLocator(ClassLoader classLoader) {
        this.resolver = new PathMatchingResourcePatternResolver(classLoader);
    }

    /**
     * @throws IllegalStateException when the globs together match no case file
     */
    public List<CaseResource> locate(List<String> globs) {
        Map<String, CaseResource> found = new LinkedHashMap<>();
        List<String> patterns = new ArrayList<>();
        for (String glob : globs) {
            String pattern = pattern(glob);
            patterns.add("classpath*:" + pattern);
            locate(pattern, root(pattern)).forEach(resource -> found.putIfAbsent(uriKey(resource), resource));
        }
        if (found.isEmpty()) {
            throw new IllegalStateException("@YamlCases globs " + globs + " match no case file (searched "
                    + patterns + "; files starting with '_' are not case files)");
        }
        return found.values().stream()
                .sorted(Comparator.comparing((CaseResource resource) -> resource.source().root())
                        .thenComparing(resource -> resource.source().relativePath()))
                .toList();
    }

    static String pattern(String glob) {
        String pattern = glob.startsWith("/") ? glob.substring(1) : glob;
        if (pattern.endsWith(YAML_SUFFIX)) {
            return pattern;
        }
        if (pattern.endsWith("/**")) {
            return pattern + "/*" + YAML_SUFFIX;
        }
        return (pattern.endsWith("/") ? pattern : pattern + "/") + "**/*" + YAML_SUFFIX;
    }

    /** The folder prefix of a pattern before its first wildcard segment, ending in '/' (empty for the root). */
    static String root(String pattern) {
        int wildcard = indexOfWildcard(pattern);
        String fixed = wildcard < 0 ? pattern : pattern.substring(0, wildcard);
        return fixed.substring(0, fixed.lastIndexOf('/') + 1);
    }

    private static int indexOfWildcard(String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            if ("*?{[".indexOf(pattern.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    private List<CaseResource> locate(String pattern, String root) {
        try {
            List<String> rootUrls = new ArrayList<>();
            for (Resource rootDir : resolver.getResources("classpath*:" + root)) {
                rootUrls.add(rootDir.getURL().toString());
            }
            List<CaseResource> resources = new ArrayList<>();
            for (Resource resource : resolver.getResources("classpath*:" + pattern)) {
                String fileName = resource.getFilename();
                if (fileName != null && !fileName.startsWith("_")) {
                    resources.add(new CaseResource(resource, source(resource, root, rootUrls)));
                }
            }
            return resources;
        } catch (IOException e) {
            throw new UncheckedIOException("can't resolve case files for " + pattern, e);
        }
    }

    private static CaseSource source(Resource resource, String root, List<String> rootUrls) throws IOException {
        String url = resource.getURL().toString();
        String relativePath = rootUrls.stream()
                .filter(url::startsWith)
                .max(Comparator.comparingInt(String::length))
                .map(rootUrl -> url.substring(rootUrl.length()))
                .orElseThrow(() -> new IllegalStateException(url + " is not below a classpath root of " + root));
        relativePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return new CaseSource(classpathUri(root + relativePath), root, relativePath);
    }

    private static URI classpathUri(String path) {
        try {
            return new URI("classpath", "/" + path, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("invalid case file path " + path, e);
        }
    }

    private static String uriKey(CaseResource resource) {
        return resource.source().testSourceUri().toString();
    }
}
