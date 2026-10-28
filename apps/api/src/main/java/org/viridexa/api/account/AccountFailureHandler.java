package org.viridexa.api.account;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataAccessException;
@RestControllerAdvice(assignableTypes=AccountController.class) @Order(-10)
public class AccountFailureHandler {
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) public ResponseEntity<Map<String,String>> invalid(Exception ignored){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("code","INVALID_ACCOUNT_INPUT","message","请检查邮箱、昵称和密码格式"));}
 @ExceptionHandler(DataAccessException.class) public ResponseEntity<Map<String,String>> unavailable(DataAccessException error){org.slf4j.LoggerFactory.getLogger(AccountFailureHandler.class).warn("Account operation unavailable: {}",error.getClass().getSimpleName());return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(Map.of("code","ACCOUNT_UNAVAILABLE","message","账户服务暂不可用，请稍后重试"));}
}
