package org.viridexa.api.protection;
public class ProtectionException extends RuntimeException {
 final int status;final String code;final int retryAfter;
 public ProtectionException(int status,String code,String message,int retryAfter){super(message);this.status=status;this.code=code;this.retryAfter=retryAfter;}
}
