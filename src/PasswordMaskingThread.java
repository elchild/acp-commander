package acpcommander;

import java.io.IOException;

public class PasswordMaskingThread extends Thread {
  public boolean typing = true;

  public void run() {
    while (this.typing) {
      System.out.print("\rPassword: **********\rPassword: ");
      try {
        sleep(50L);
      } catch (InterruptedException ie) {
      }
    }
  }

  public String getPassword() throws IOException {
    String pass = "";

    while (true) {
      char c = (char)System.in.read();

      this.typing = false;

      if (c == '\n' || c == '\r') {
        break;
      }
      pass = pass + c;
    }
    return pass;
  }
}


