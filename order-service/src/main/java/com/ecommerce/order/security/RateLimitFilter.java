package com.ecommerce.order.security;
import com.ecommerce.order.dto.ErrorResponse; import com.fasterxml.jackson.databind.ObjectMapper; import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import java.time.Instant; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.MediaType; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component public class RateLimitFilter extends OncePerRequestFilter {
 private record Bucket(long start,AtomicLong count){} private final ConcurrentHashMap<String,Bucket> buckets=new ConcurrentHashMap<>(); private final int limit; private final long window; private final ObjectMapper mapper; private final AtomicLong calls=new AtomicLong();
 public RateLimitFilter(@Value("${app.rate-limit.requests:60}") int l,@Value("${app.rate-limit.seconds:60}") int s,ObjectMapper m){if(l<1||s<1)throw new IllegalArgumentException("Rate limit values must be positive.");limit=l;window=s*1000L;mapper=m;}
 @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain c)throws ServletException,IOException{
  long now=System.currentTimeMillis();String ip=q.getRemoteAddr();long[] retry={0};
  buckets.compute(ip,(k,old)->{Bucket b=old==null||now-old.start()>=window?new Bucket(now,new AtomicLong()):old;long n=b.count().incrementAndGet();if(n>limit)retry[0]=Math.max(1,(window-(now-b.start())+999)/1000);return b;});
  if(retry[0]>0){r.setStatus(429);r.setHeader("Retry-After",Long.toString(retry[0]));r.setContentType(MediaType.APPLICATION_JSON_VALUE);mapper.writeValue(r.getOutputStream(),new ErrorResponse(Instant.now(),429,"RATE_LIMIT_EXCEEDED","Rate limit exceeded; retry after "+retry[0]+" seconds."));return;}
  if(calls.incrementAndGet()%256==0)buckets.entrySet().removeIf(e->now-e.getValue().start()>window*2);c.doFilter(q,r);
 }
}