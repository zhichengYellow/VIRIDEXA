package org.viridexa.api.governance;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ReviewRules {
 private ReviewRules() {}
 public static void transition(String state,String action) {
  boolean allowed=switch(state) {
   case "PENDING" -> Set.of("APPROVED","REJECTED","NEEDS_EVIDENCE").contains(action);
   case "NEEDS_EVIDENCE" -> Set.of("APPROVED","REJECTED").contains(action);
   case "APPROVED" -> action.equals("REVOKED");
   case "REJECTED","REVOKED" -> action.equals("NEEDS_EVIDENCE");
   default -> false;
  };
  if(!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT,"审核状态已变化或该转换不允许");
 }
 public static void lipid(String kind,String unit,String basis,BigDecimal value) {
  if(basis==null||basis.isBlank()) invalid("计量基础缺失");
  if(value==null) invalid("缺失测量值不能批准为数值事实");
  if(value.signum()<0) invalid("数值不能为负");
  if(Set.of("OIL_CONTENT","OIL_FA_COMPOSITION","FAME_COMPOSITION").contains(kind)) {
   if(!"%".equals(unit)||value.compareTo(new BigDecimal("100"))>0) invalid("百分比单位或范围不适用");
  } else if("FOOD_NUTRIENT".equals(kind)) {
   if(unit==null||!Set.of("G","MG","UG","g","mg","µg").contains(unit)) invalid("食品营养单位不受支持");
  } else invalid("未知观测种类");
 }
 public static void invalid(String message) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message); }
}
