package com.benchos.api.shared.pix;

import com.benchos.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixBrCodeGeneratorTest {

    @Test
    void computesStandardCrc16CcittFalseCheckValue() {
        int crc = PixBrCodeGenerator.crc16Ccitt("123456789".getBytes(StandardCharsets.UTF_8));
        assertThat(crc).isEqualTo(0x29B1);
    }

    @Test
    void generatesValidStaticEmvPayloadWithAccentsNormalizedAndValidCrc() {
        String payload = PixBrCodeGenerator.generate(
                "12345678000199",
                "Oficina São José Eletrodomésticos Ltda",
                "São Paulo",
                new BigDecimal("145.50"),
                "OS1042"
        );

        assertThat(payload).startsWith("000201");
        assertThat(payload).contains("0014br.gov.bcb.pix011412345678000199");
        assertThat(payload).contains("520400005303986");
        assertThat(payload).contains("5406145.50");
        assertThat(payload).contains("5802BR");
        assertThat(payload).contains("5925Oficina Sao Jose Eletrodo");
        assertThat(payload).contains("6009Sao Paulo");
        assertThat(payload).contains("62100506OS1042");
        assertThat(payload).contains("6304");

        // Verify trailing CRC matches recomputed CRC over prefix
        String withoutCrc = payload.substring(0, payload.length() - 4);
        String crcHex = payload.substring(payload.length() - 4);
        int expectedCrc = PixBrCodeGenerator.crc16Ccitt(withoutCrc.getBytes(StandardCharsets.UTF_8));
        assertThat(crcHex).isEqualTo(String.format("%04X", expectedCrc));

        // Verify compact SVG QR rendering
        String svg = PixBrCodeGenerator.renderQrSvg(payload);
        assertThat(svg).startsWith("<svg ").contains("<path d=\"").endsWith("</svg>");
    }

    @Test
    void rejectsPixKeyContainingWhitespace() {
        assertThatThrownBy(() -> PixBrCodeGenerator.normalizeAndValidatePixKey("11 99999-8888"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
