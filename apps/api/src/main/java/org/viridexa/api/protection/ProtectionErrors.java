package org.viridexa.api.protection;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestControllerAdvice
public class ProtectionErrors {
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public ResponseEntity<Map<String,String>> invalid(Exception ignored){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("code","INVALID_REQUEST","message","请求格式不正确，请检查后重试。"));}
 @ExceptionHandler(ProtectionException.class) public ResponseEntity<Map<String,String>> problem(ProtectionException e){var b=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore());if(e.retryAfter>0)b.header("Retry-After",String.valueOf(e.retryAfter));return b.body(Map.of("code",e.code,"message",e.getMessage()));}
}
