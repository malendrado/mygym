package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SvgSanitizerTest {

    @Test
    void entityEncodedJavascriptSchemeIsRejected() {
        assertThat(SvgSanitizer.validate("<svg><a href=\"jav&#97;script:alert(1)\"><path d=\"M0 0\"/></a></svg>")).isNotNull();
        assertThat(SvgSanitizer.validate("<svg><a href=\"&#x6A;avascript&#58;alert(1)\"><path d=\"M0 0\"/></a></svg>")).isNotNull();
    }

    @Test
    void externalLoadersAndSvgDataUrisAreRejected() {
        assertThat(SvgSanitizer.validate("<svg><link rel=\"stylesheet\" href=\"https://x.cl/a.css\"/></svg>")).isNotNull();
        assertThat(SvgSanitizer.validate("<svg><image href=\"data:image/svg+xml;base64,AAAA\"/></svg>")).isNotNull();
    }

    @Test
    void ordinaryLogoWithGradientAndMetadataStillPasses() {
        assertThat(SvgSanitizer.validate("<svg xmlns=\"http://www.w3.org/2000/svg\"><metadata>x</metadata>"
                + "<linearGradient id=\"g\"><stop offset=\"0\"/></linearGradient><rect fill=\"url(#g)\"/></svg>")).isNull();
    }
}
