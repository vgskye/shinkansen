package agency.highlysuspect.minivan;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Util {
	public static void cpy(InputStream in, OutputStream out) throws IOException {
		byte[] shuttle = new byte[4096];
		int read;
		while((read = in.read(shuttle)) != -1) out.write(shuttle, 0, read);
	}
	
	public static void cpy(InputStream in, Path out) throws IOException {
		try(OutputStream outS = new BufferedOutputStream(Files.newOutputStream(out))) {
			cpy(in, outS);
		}
	}
}
