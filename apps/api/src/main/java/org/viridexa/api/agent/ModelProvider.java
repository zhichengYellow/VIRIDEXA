package org.viridexa.api.agent;
import java.time.Duration;
import java.util.*;
public interface ModelProvider {
 boolean configured();
 String modelName();
 AgentContract.ModelTurn complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools,Duration timeout) throws Exception;
}
