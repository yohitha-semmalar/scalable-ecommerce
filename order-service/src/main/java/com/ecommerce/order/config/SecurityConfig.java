package com.ecommerce.order.config;
import com.ecommerce.order.dto.ErrorResponse; import com.ecommerce.order.security.*; import com.fasterxml.jackson.databind.ObjectMapper; import java.time.Instant; import org.springframework.context.annotation.*; import org.springframework.http.MediaType; import org.springframework.security.config.http.SessionCreationPolicy; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.web.*; import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration public class SecurityConfig {
 @Bean SecurityFilterChain chain(HttpSecurity h,RateLimitFilter rate,JwtFilter jwt,ObjectMapper mapper)throws Exception{
 h.csrf(c->c.disable()).httpBasic(c->c.disable()).formLogin(c->c.disable()).logout(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 .authorizeHttpRequests(a->a.requestMatchers("/health").permitAll().anyRequest().authenticated())
 .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->write(r,mapper,401,"UNAUTHORIZED","Authentication is required.")).accessDeniedHandler((q,r,x)->write(r,mapper,403,"FORBIDDEN","Access denied.")))
 .addFilterBefore(rate,UsernamePasswordAuthenticationFilter.class).addFilterAfter(jwt,UsernamePasswordAuthenticationFilter.class);return h.build();}
 private void write(jakarta.servlet.http.HttpServletResponse r,ObjectMapper m,int s,String code,String msg)throws java.io.IOException{r.setStatus(s);r.setContentType(MediaType.APPLICATION_JSON_VALUE);m.writeValue(r.getOutputStream(),new ErrorResponse(Instant.now(),s,code,msg));}
}