package vn.edu.utex.bookstore.image;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.utex.bookstore.common.Problem;
class ReviewVideoTest {
  @Test void rejectsSpoofedOrOversizedVideoBeforeAnyUpload() throws Exception {
    var calls=new AtomicInteger();var storage=new CloudinaryImageStorage((data,options)->{calls.incrementAndGet();return Map.of();},"WEPR");
    assertThrows(Problem.class,()->storage.storeVideo(new ByteArrayInputStream(new byte[20]),20));
    assertThrows(Problem.class,()->storage.storeVideo(new ByteArrayInputStream(new byte[0]),5*1024*1024+1));assertEquals(0,calls.get());
  }
  @Test void providerMustDecodeVideoAndReturnHttpsVideoAsset() throws Exception {
    byte[] mp4=new byte[24];mp4[4]='f';mp4[5]='t';mp4[6]='y';mp4[7]='p';
    var storage=new CloudinaryImageStorage((data,options)->{assertEquals("video",options.get("resource_type"));assertEquals(false,options.get("overwrite"));
      return Map.of("secure_url","https://res.cloudinary.com/demo/video/upload/sample.mp4","resource_type","video","public_id","sample");},"WEPR");
    assertEquals("video",storage.storeVideo(new ByteArrayInputStream(mp4),24).resourceType());
    var malicious=new CloudinaryImageStorage((data,options)->Map.of("secure_url","javascript:alert(1)","resource_type","video"),"WEPR");
    assertThrows(IOException.class,()->malicious.storeVideo(new ByteArrayInputStream(mp4),24));
  }
}
