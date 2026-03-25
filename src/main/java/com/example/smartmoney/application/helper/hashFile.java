package com.example.smartmoney.application.helper;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;

public class hashFile {

    private hashFile() {
        // prevent instantiate
    }

    public static String hashFileMD5 (MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }

            byte[] digest = md.digest();

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error hashing file", e);
        }
    }
}