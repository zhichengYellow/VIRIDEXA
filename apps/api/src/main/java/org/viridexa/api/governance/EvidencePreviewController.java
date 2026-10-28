package org.viridexa.api.governance;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/science/sources")
public class EvidencePreviewController {
 private final JdbcTemplate jdbc;private final EvidenceIntegrity integrity;
 public EvidencePreviewController(JdbcTemplate jdbc,EvidenceIntegrity integrity){this.jdbc=jdbc;this.integrity=integrity;}
 @GetMapping("/{id}/preview") public ResponseEntity<Resource> preview(@PathVariable String id)throws Exception{
  ReviewService.validateId(id);var rows=jdbc.queryForList("SELECT path,sha256,license_state,integrity_state FROM scientific_source_version WHERE id=?",id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  var source=rows.getFirst();if(!"PERMITTED".equals(source.get("license_state"))||!"VERIFIED".equals(source.get("integrity_state")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"证据许可或完整性尚未允许预览");
  var path=integrity.verify((String)source.get("path"),(String)source.get("sha256"));String name=path.getFileName().toString();String lower=name.toLowerCase(Locale.ROOT);
  MediaType type=lower.endsWith(".pdf")?MediaType.APPLICATION_PDF:lower.endsWith(".png")?MediaType.IMAGE_PNG:(lower.endsWith(".jpg")||lower.endsWith(".jpeg"))?MediaType.IMAGE_JPEG:null;
  if(type==null||Files.size(path)>20*1024*1024)throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"此格式或大小不支持受控预览");
  return ResponseEntity.ok().contentType(type).header("Cache-Control","private, no-store").header("Content-Security-Policy","sandbox").header("Content-Disposition",ContentDisposition.inline().filename(name,StandardCharsets.UTF_8).build().toString()).body(new FileSystemResource(path));
 }
}
