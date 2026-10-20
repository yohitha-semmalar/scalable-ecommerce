package com.teamshop.product.config;
import com.teamshop.product.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
public class SecurityConfig {
 private final JwtAuthenticationFilter filter;
 public SecurityConfig(JwtAuthenticationFilter filter){this.filter=filter;}
 @Bean SecurityFilterChain filterChain(HttpSecurity http)throws Exception{
  return http.csrf(c->c.disable()).httpBasic(b->b.disable()).formLogin(f->f.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/api/health").permitAll().requestMatchers(HttpMethod.GET,"/api/products","/api/products/**","/api/search").permitAll().requestMatchers("/api/shards/**").authenticated().requestMatchers(HttpMethod.POST,"/api/products").authenticated().anyRequest().authenticated())
   .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
 }
}
