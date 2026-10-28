package org.viridexa.api.governance;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class DatabaseFailureHandler {
 @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
 public ResponseEntity<Map<String,String>> invalidParameter(jakarta.validation.ConstraintViolationException e){return ResponseEntity.badRequest().body(Map.of("error","INVALID_PARAMETER","message","请求参数无效"));}
 @ExceptionHandler(DataAccessException.class)
 public ResponseEntity<Map<String,String>> unavailable(DataAccessException e){
  org.slf4j.LoggerFactory.getLogger(DatabaseFailureHandler.class).warn("Database operation failed: {}",e.getClass().getSimpleName());
  for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql && (sql.getErrorCode()==1213||sql.getErrorCode()==1205||"40001".equals(sql.getSQLState())))return ResponseEntity.status(409).body(Map.of("error","CONCURRENT_REVIEW","message","并发审核冲突，请刷新后重试"));
  return ResponseEntity.status(503).body(Map.of("error","DATABASE_UNAVAILABLE","message","数据服务暂不可用，审核未确认，请刷新检查历史后重试"));}
}
