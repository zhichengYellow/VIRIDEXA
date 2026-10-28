package org.viridexa.api.agent;
import java.util.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
 private final AgentService agent;
 @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<org.viridexa.api.workbench.WorkbenchService> workbench;
 public AgentController(AgentService agent){this.agent=agent;}
 public record Question(@NotBlank @Size(max=1500) String question,org.viridexa.api.workbench.WorkbenchContract.AgentContext context){}
 @GetMapping("/meta") public Map<String,Object> meta(HttpServletResponse response){response.setHeader("Cache-Control","no-store");return agent.meta();}
 @PostMapping("/runs") public ResponseEntity<AgentContract.RunView> start(@Valid @RequestBody Question question,HttpServletRequest request,Authentication auth){String owner=owner(request);String budget=auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)?"user:"+auth.getName():"ip:"+request.getRemoteAddr();return ResponseEntity.status(202).cacheControl(CacheControl.noStore()).body(agent.start(owner,budget,question.question(),question.context()==null?Map.of():workbench.getObject().validateAgentContext(question.context())));}
 @GetMapping("/runs/{id}") public ResponseEntity<AgentContract.RunView> get(@PathVariable String id,HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(agent.get(owner(request),id));}
 @PostMapping("/runs/{id}/cancel") public ResponseEntity<AgentContract.RunView> cancel(@PathVariable String id,HttpServletRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(agent.cancel(owner(request),id));}
 private String owner(HttpServletRequest request){HttpSession session=request.getSession();synchronized(session){String value=(String)session.getAttribute("agentOwner");if(value==null){value=UUID.randomUUID().toString();session.setAttribute("agentOwner",value);}return value;}}
}
