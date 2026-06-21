package com.aegis.authserver.jwk;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Minimal PEM (de)serialization for RSA keys. Public keys use the {@code X.509}
 * SubjectPublicKeyInfo encoding; private keys use {@code PKCS#8}.
 */
final class PemUtils {

    private static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_END = "-----END PUBLIC KEY-----";
    private static final String PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_END = "-----END PRIVATE KEY-----";

    private PemUtils() {
    }

    static String toPem(RSAPublicKey key) {
        return wrap(PUBLIC_BEGIN, PUBLIC_END, key.getEncoded());
    }

    static String toPem(RSAPrivateKey key) {
        return wrap(PRIVATE_BEGIN, PRIVATE_END, key.getEncoded());
    }

    static RSAPublicKey publicKeyFromPem(String pem) {
        byte[] der = decode(pem, PUBLIC_BEGIN, PUBLIC_END);
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA public key PEM", ex);
        }
    }

    static RSAPrivateKey privateKeyFromPem(String pem) {
        byte[] der = decode(pem, PRIVATE_BEGIN, PRIVATE_END);
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA private key PEM", ex);
        }
    }

    private static String wrap(String begin, String end, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return begin + "\n" + body + "\n" + end + "\n";
    }

    private static byte[] decode(String pem, String begin, String end) {
        String base64 = pem.replace(begin, "").replace(end, "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
