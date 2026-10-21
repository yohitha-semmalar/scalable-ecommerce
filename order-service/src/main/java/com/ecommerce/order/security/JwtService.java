package com.ecommerce.order.security;
import io.jsonwebtoken.*; import io.jsonwebtoken.io.Decoders; import io.jsonwebtoken.security.Keys; import java.security.Key; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
@Service public class JwtService {
 private final javax.crypto.SecretKey key;
 public JwtService(@Value("${app.jwt.secret}") String secret){try{byte[] b=Decoders.BASE64.decode(secret);if(b.length<32)throw new IllegalArgumentException();key=Keys.hmacShaKeyFor(b);}catch(IllegalArgumentException e){throw new IllegalArgumentException("JWT_SECRET_BASE64 must be Base64 decoding to at least 32 bytes.");}}
 public String validateAndGetSubject(String token){Claims c=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();if(c.getSubject()==null||c.getSubject().isBlank())throw new JwtException("Missing subject");return c.getSubject();}
}