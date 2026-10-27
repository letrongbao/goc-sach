package vn.edu.utex.bookstore.image;
import java.io.*;
import vn.edu.utex.bookstore.common.Problem;
public final class VideoValidation {
  private VideoValidation() {}
  public static byte[] read(InputStream input,long size) throws IOException {
    int maximum=5*1024*1024;
    if(size<12||size>maximum)throw Problem.invalid("Video MP4 tối đa 5 MB.");
    byte[] data=input.readNBytes(maximum+1);
    if(data.length!=size||data.length>maximum||data[4]!='f'||data[5]!='t'||data[6]!='y'||data[7]!='p')
      throw Problem.invalid("Tệp video phải là MP4 hợp lệ.");
    // The provider decodes the container; a filename or client MIME alone is never trusted.
    return data;
  }
}
