package org.course;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Versioned PBKDF2 hashes; neither plaintext passwords nor session tokens enter logs. */
public final class Passwords {
    private static final SecureRandom RANDOM = new SecureRandom();
    public static String token() { byte[] b=new byte[24]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    public static String hash(String password) {
        byte[] salt=new byte[16]; RANDOM.nextBytes(salt);
        return "pbkdf2$210000$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(password,salt,210000));
    }
    public static boolean matches(String password,String stored) {
        try { String[] p=stored.split("\\$"); return MessageDigest.isEqual(Base64.getDecoder().decode(p[3]),derive(password,Base64.getDecoder().decode(p[2]),Integer.parseInt(p[1]))); }
        catch(RuntimeException e) { return false; }
    }
    private static byte[] derive(String p,byte[] salt,int iterations) {
        PBEKeySpec spec=new PBEKeySpec(p.toCharArray(),salt,iterations,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch(Exception e) { throw new IllegalStateException("Password hashing unavailable",e); }
        finally { spec.clearPassword(); }
    }
    private Passwords() {}
}
