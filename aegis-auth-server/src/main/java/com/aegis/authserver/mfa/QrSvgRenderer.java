package com.aegis.authserver.mfa;

import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * Renders a QR code as a self-contained inline SVG (one path, viewBox in module units,
 * scales to any size losslessly). SVG instead of a PNG data-URI keeps the page free of
 * any imaging stack and the markup inspectable. The caller must ensure a light
 * background behind the dark modules — scanners need the contrast.
 */
public final class QrSvgRenderer {

    private static final int QUIET_ZONE_MODULES = 4; // spec minimum for reliable scans

    private QrSvgRenderer() {
    }

    public static String render(String content) {
        BitMatrix matrix = encode(content);
        int size = matrix.getWidth(); // square, quiet zone included
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(',').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
                + "\" role=\"img\" aria-label=\"QR code for authenticator app enrollment\""
                + " shape-rendering=\"crispEdges\">"
                + "<rect width=\"" + size + "\" height=\"" + size + "\" fill=\"#ffffff\"/>"
                + "<path d=\"" + path + "\" fill=\"#0b1120\"/>"
                + "</svg>";
    }

    private static BitMatrix encode(String content) {
        try {
            // Width/height 0: let the encoder pick the minimal version for the content;
            // the returned matrix already carries the requested quiet zone.
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, QUIET_ZONE_MODULES));
        } catch (WriterException e) {
            throw new IllegalArgumentException("Content not QR-encodable", e);
        }
    }
}
