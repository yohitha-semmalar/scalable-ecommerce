package com.ecommerce.order.security;
import com.ecommerce.order.dto.ErrorResponse; import com.fasterxml.jackson.databind.ObjectMapper; import io.jsonwebtoken.JwtException; import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import java.time.Instant;
import org.springframework.http.MediaType; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.AuthorityUtils; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component public class JwtFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final ObjectMapper mapper; public JwtFilter(JwtService j,ObjectMapper m){jwt=j;mapper=m;}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){return "/health".equals(r.getServletPath());}
 @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse s,FilterChain c)throws ServletException,IOException{
  String h=q.getHeader("Authorization");if(h==null||!h.startsWith("Bearer ")||h.substring(7).isBlank()){unauthorized(s);return;}
  String uid;
  try{uid=jwt.validateAndGetSubject(h.substring(7).trim());}
  catch(JwtException|IllegalArgumentException e){SecurityContextHolder.clearContext();unauthorized(s);return;}
  q.setAttribute("authenticatedUserId",uid);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(uid,null,AuthorityUtils.NO_AUTHORITIES));
  c.doFilter(q,s);
 }
 private void unauthorized(HttpServletResponse s)throws IOException{s.setStatus(401);s.setContentType(MediaType.APPLICATION_JSON_VALUE);mapper.writeValue(s.getOutputStream(),new ErrorResponse(Instant.now(),401,"UNAUTHORIZED","The Bearer token is missing, invalid or expired."));}
}