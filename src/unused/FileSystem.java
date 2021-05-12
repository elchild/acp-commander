package unused;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.FileChannel;

public class FileSystem {
  public static void main() {}

  public static void copyFile(String src, String target) throws IOException {
    FileChannel srcChannel = (new FileInputStream(src)).getChannel();
    FileChannel destChannel = (new FileOutputStream(target)).getChannel();

    srcChannel.transferTo(0L, srcChannel.size(), destChannel);

    srcChannel.close();
    destChannel.close();
  }


  public static void copyFile(URL src, String target) throws IOException {
    int len = 32768;
    byte[] buff = new byte[len];
    InputStream fis = src.openStream();
    FileOutputStream fos = new FileOutputStream(target);
    while (0 < (len = fis.read(buff))) {
      fos.write(buff, 0, len);
    }
    fos.flush();
    fos.close();
    fis.close();
  }



  public static boolean delFile(String afile) {
    return (new File(afile)).delete();
  }

  public static boolean mkdir(String newdir) {
    return (new File(newdir)).mkdir();
  }

  public static boolean mkdirs(String newdir) {
    return (new File(newdir)).mkdirs();
  }

  void display(String filename) throws FileNotFoundException {
    try {
      int length = 0;
      FileInputStream file = null;

      file = new FileInputStream(filename);

      byte[] buffer = new byte[file.available()];

      length = file.read(buffer);

      System.out.write(buffer, 0, length);

      file.close();
    } catch (IOException IOE) {
    }
  }
}
