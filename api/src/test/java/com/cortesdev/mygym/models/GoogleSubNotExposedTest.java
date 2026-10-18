package com.cortesdev.mygym.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.models.dto.MemberResponse;
import java.io.File;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * googleSub es el identificador estable de la cuenta de Google de la persona: solo debe servir para el login. Este test
 * deja documentado (auditoría 2026-10-18) que ningún DTO de respuesta lo trae y que AppUser no tiene toString que lo
 * vuelque a un log por accidente.
 */
class GoogleSubNotExposedTest {

    @Test
    void noDtoCarriesGoogleSub() throws Exception {
        File dir = new File(new File(MemberResponse.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
                MemberResponse.class.getPackageName().replace('.', '/'));
        File[] classes = dir.listFiles((d, name) -> name.endsWith(".class"));
        assertThat(classes).isNotEmpty();
        for (File file : classes) {
            String name = MemberResponse.class.getPackageName() + "." + file.getName().replace(".class", "");
            Class<?> dto = Class.forName(name);
            Arrays.stream(dto.getDeclaredFields())
                    .forEach(f -> assertThat(f.getName()).as(name).doesNotContainIgnoringCase("googleSub"));
            if (dto.isRecord()) {
                for (RecordComponent c : dto.getRecordComponents()) {
                    assertThat(c.getName()).as(name).doesNotContainIgnoringCase("googleSub");
                }
            }
        }
    }

    @Test
    void appUserHasNoToStringThatWouldLeakItIntoLogs() {
        assertThat(Arrays.stream(AppUser.class.getDeclaredMethods()).map(m -> m.getName())).doesNotContain("toString");
    }
}
