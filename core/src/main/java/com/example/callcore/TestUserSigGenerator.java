package com.example.callcore;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.Deflater;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates UserSig for local testing only. Production apps must request UserSig from a backend.
 */
public final class TestUserSigGenerator {
    public static final int SDK_APP_ID = 1600157919;

    private static final int EXPIRE_TIME_SECONDS = 604800;
    private static final String SDK_SECRET_KEY =
            "447b0c09ba7d7782ff02c8dc5adb63e2e6e0eb997378d300ffdd3f26ec39fc50";

    private TestUserSigGenerator() {
    }

    public static String generate(String userId) {
        return generateSignature(SDK_APP_ID, userId, EXPIRE_TIME_SECONDS, SDK_SECRET_KEY);
    }

    private static String generateSignature(long sdkAppId, String userId, long expire,
                                            String secretKey) {
        long currentTime = System.currentTimeMillis() / 1000;
        JSONObject document = new JSONObject();
        try {
            document.put("TLS.ver", "2.0");
            document.put("TLS.identifier", userId);
            document.put("TLS.sdkappid", sdkAppId);
            document.put("TLS.expire", expire);
            document.put("TLS.time", currentTime);
            document.put("TLS.sig", hmacSha256(sdkAppId, userId, currentTime, expire, secretKey));
        } catch (JSONException e) {
            return "";
        }

        Deflater compressor = new Deflater();
        compressor.setInput(document.toString().getBytes(StandardCharsets.UTF_8));
        compressor.finish();
        byte[] compressed = new byte[2048];
        int length = compressor.deflate(compressed);
        compressor.end();
        return new String(base64UrlEncode(Arrays.copyOfRange(compressed, 0, length)),
                StandardCharsets.UTF_8);
    }

    private static String hmacSha256(long sdkAppId, String userId, long currentTime,
                                     long expire, String secretKey) {
        String content = "TLS.identifier:" + userId + "\n"
                + "TLS.sdkappid:" + sdkAppId + "\n"
                + "TLS.time:" + currentTime + "\n"
                + "TLS.expire:" + expire + "\n";
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.encodeToString(hmac.doFinal(content.getBytes(StandardCharsets.UTF_8)),
                    Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "";
        }
    }

    private static byte[] base64UrlEncode(byte[] input) {
        byte[] base64 = Base64.encode(input, Base64.NO_WRAP);
        for (int i = 0; i < base64.length; i++) {
            if (base64[i] == '+') {
                base64[i] = '*';
            } else if (base64[i] == '/') {
                base64[i] = '-';
            } else if (base64[i] == '=') {
                base64[i] = '_';
            }
        }
        return base64;
    }
}
