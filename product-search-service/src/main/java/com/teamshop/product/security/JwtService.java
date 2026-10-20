package com.teamshop.product.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Date;
@Service
public class JwtService {
 private final SecretKey key;
 public JwtService(@Value("${app.jwt.secret}") String secret){key=Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));}
 public boolean valid(String token){try{Claims c=parse(token);return c.getSubject()!=null&&c.getExpiration()!=null&&c.getExpiration().after(new Date());}catch(RuntimeException e){return false;}}
 private Claims parse(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
 public String subject(String token){return parse(token).getSubject();}
}
