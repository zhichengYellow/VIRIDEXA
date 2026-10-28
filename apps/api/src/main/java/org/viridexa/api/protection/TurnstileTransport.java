package org.viridexa.api.protection;
import java.net.*;import java.net.http.*;import java.nio.ByteBuffer;import java.nio.charset.StandardCharsets;import java.time.Duration;import java.util.*;import java.util.concurrent.*;import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
/** Fixed production HTTPS destination; bounded body, concurrency in verifier, no credential diagnostics. */
@Component
public class TurnstileTransport {
 private final URI endpoint;private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 public TurnstileTransport(){endpoint=URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");}
 // Package-local seam for deterministic wire tests, never exposed as configuration or a tool parameter.
 TurnstileTransport(URI fixture){endpoint=fixture;}
 public Map<String,Object> verify(String secret,String token,int seconds)throws Exception{
  var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(seconds)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("secret="+URLEncoder.encode(secret,StandardCharsets.UTF_8)+"&response="+URLEncoder.encode(token,StandardCharsets.UTF_8))).build();
  var pending=client.sendAsync(request,info->new LimitedBody());HttpResponse<byte[]> response;
  try{response=pending.get(seconds,TimeUnit.SECONDS);}catch(TimeoutException e){pending.cancel(true);throw new HttpTimeoutException("Verification deadline exceeded");}catch(InterruptedException e){pending.cancel(true);throw e;}
  if(response.statusCode()!=200)throw new IllegalStateException("Verification unavailable");return JsonMapper.builder().build().readValue(response.body(),Map.class);
 }
 private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return result;}public void onSubscribe(Flow.Subscription s){subscription=s;s.request(Long.MAX_VALUE);}
  public void onNext(List<ByteBuffer> buffers){for(var buffer:buffers){if(buffer.remaining()+bytes.size()>16384){subscription.cancel();result.completeExceptionally(new IllegalStateException("Verification response too large"));return;}byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);}}
  public void onError(Throwable t){result.completeExceptionally(t);}public void onComplete(){result.complete(bytes.toByteArray());}
 }
}
