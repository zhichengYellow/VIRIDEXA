package org.viridexa.api.governance;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class EvidenceIntegrityTest {
 @TempDir Path temp;
 private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 @Test void exactFileHashRequired()throws Exception{Files.writeString(temp.resolve("source.txt"),"fixture");var verifier=new EvidenceIntegrity(temp.toString());assertEquals(temp.resolve("source.txt").toRealPath(),verifier.verify("source.txt",hash("fixture".getBytes())));assertThrows(ResponseStatusException.class,()->verifier.verify("source.txt","0".repeat(64)));}
 @Test void traversalAndMissingAreRejected()throws Exception{var verifier=new EvidenceIntegrity(temp.toString());assertThrows(ResponseStatusException.class,()->verifier.verify("../other","0".repeat(64)));assertThrows(ResponseStatusException.class,()->verifier.verify("missing","0".repeat(64)));}
 @Test void changedFileInvalidatesCachedEvidence()throws Exception{Files.writeString(temp.resolve("source.txt"),"old");var verifier=new EvidenceIntegrity(temp.toString());var original=hash("old".getBytes());verifier.verify("source.txt",original);Files.writeString(temp.resolve("source.txt"),"changed");assertThrows(ResponseStatusException.class,()->verifier.verify("source.txt",original));}
}
