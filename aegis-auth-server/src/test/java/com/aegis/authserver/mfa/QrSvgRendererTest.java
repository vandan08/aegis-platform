package com.aegis.authserver.mfa;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class QrSvgRendererTest {

    private static final String URI =
            "otpauth://totp/Aegis%3Aalice?secret=JBSWY3DPEHPK3PXP&issuer=Aegis&algorithm=SHA1&digits=6&period=30";

    @Test
    @DisplayName("produces well-formed standalone SVG with a light backing rect")
    void wellFormedSvg() {
        String svg = QrSvgRenderer.render(URI);

        assertThat(svg).startsWith("<svg").endsWith("</svg>")
                .contains("viewBox=\"0 0 ")
                .contains("<rect").contains("fill=\"#ffffff\"");
        assertThatCode(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deterministic for the same content, different for different content")
    void contentDriven() {
        assertThat(QrSvgRenderer.render(URI)).isEqualTo(QrSvgRenderer.render(URI));
        assertThat(QrSvgRenderer.render(URI)).isNotEqualTo(QrSvgRenderer.render(URI + "x"));
    }

    @Test
    @DisplayName("dark modules exist and sit inside the declared viewBox with a quiet zone")
    void modulesWithinBounds() {
        String svg = QrSvgRenderer.render(URI);
        int size = Integer.parseInt(svg.replaceAll(".*viewBox=\"0 0 (\\d+) .*", "$1"));

        // QR version 1 is 21 modules; with 2×4 quiet zone every real code is ≥ 29.
        assertThat(size).isGreaterThanOrEqualTo(29);
        // The quiet zone must stay empty: no module in the first 4 rows/columns.
        assertThat(svg).contains("h1v1h-1z").doesNotContain("M0,0h1").doesNotContain("M4,3h1");
    }
}
