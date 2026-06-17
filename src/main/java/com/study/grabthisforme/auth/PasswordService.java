package com.study.grabthisforme.auth;

import com.study.grabthisforme.common.ApiException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private static final int SALT_LENGTH = 16;
    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(rawPassword.toCharArray(), salt);
        return Base64.getEncoder().encodeToString(salt) + "." + Base64.getEncoder().encodeToString(hash);
    }

    public boolean matches(String rawPassword, String hashedPassword) {
        if (hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        String[] parts = hashedPassword.split("\\.");
        if (parts.length != 2) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] actual = Base64.getDecoder().decode(parts[1]);
        byte[] expected = derive(rawPassword.toCharArray(), salt);
        if (actual.length != expected.length) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < actual.length; index++) {
            result |= actual[index] ^ expected[index];
        }
        return result == 0;
    }

    private byte[] derive(char[] rawPassword, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, 50001, "Password hashing unavailable");
        }
    }
}
