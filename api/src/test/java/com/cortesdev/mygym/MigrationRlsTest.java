package com.cortesdev.mygym;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Supabase expone por REST toda tabla de {@code public} sin RLS (alerta rls_disabled_in_public).
 * Falla si alguna migracion crea una tabla y ninguna migracion le activa RLS.
 */
class MigrationRlsTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern CREATE =
            Pattern.compile("create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?:public\\.)?(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RLS =
            Pattern.compile("alter\\s+table\\s+(?:only\\s+)?(?:public\\.)?(\\w+)\\s+enable\\s+row\\s+level\\s+security", Pattern.CASE_INSENSITIVE);

    @Test
    void everyCreatedTableHasRowLevelSecurityEnabled() throws IOException {
        Set<String> created = new TreeSet<>();
        Set<String> secured = new TreeSet<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(file);
                collect(CREATE, sql, created);
                collect(RLS, sql, secured);
            }
        }
        created.removeAll(secured);
        assertTrue(created.isEmpty(), "Tablas sin ENABLE ROW LEVEL SECURITY (quedan publicas en Supabase): " + created);
    }

    private static void collect(Pattern pattern, String sql, Set<String> into) {
        Matcher m = pattern.matcher(sql);
        while (m.find()) {
            into.add(m.group(1).toLowerCase());
        }
    }
}
