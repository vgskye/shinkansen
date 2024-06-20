package agency.highlysuspect.minivan.prov;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public class Props {
	public Props() {
		this(new TreeMap<>());
	}
	
	public Props(Props clone) {
		this(clone.props);
	}
	
	public Props(SortedMap<String, String> props) {
		this.props = props;
	}
	
	private final SortedMap<String, String> props;
	
	public Props set(String key, String value) {
		props.put(key, value);
		return this;
	}
	
	public Props setFile(String key, Path path) throws IOException {
		MessageDigest sha1 = sha1();
		
		try(InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
			byte[] shuttle = new byte[4096];
			int read;
			while((read = in.read(shuttle)) != -1) sha1.update(shuttle, 0, read);
		}
		
		props.put(key, "file-" + hexHash(sha1.digest()));
		return this;
	}
	
	public Props merge(Props other) {
		other.props.forEach((otherKey, otherValue) -> {
			//if my key=value matches their key=value, no need to keep both pieces of information
			if(Objects.equals(props.get(otherKey), otherValue))
				return;
			
			//otherwise we're in a situation where my key=value1 and their key=value2
			//want to keep both pieces of information, so pick an unused key
			String noConflictKey = otherKey;
			int i = 1;
			while(props.containsKey(noConflictKey)) {
				noConflictKey = otherKey + i++;
			}
			props.put(noConflictKey, otherValue);
		});
		
		return this;
	}
	
	public boolean has(String key) {
		return props.containsKey(key);
	}
	
	/**
	 * @return the empty string if the properties is empty,
	 *         or a string like "-ab4c9324" where the characters correspond to a hash of the map's contents
	 */
	public String suffix() {
		if(props.isEmpty()) return "";
		
		//obtain hasher
		MessageDigest digest = sha1();
		
		//pour data into the hasher
		digest.update((byte) props.size());
		props.forEach((k, v) -> {
			digest.update(k.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(v.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 1);
		});
		
		//hex-ify first 4 bytes of the hash
		return "-" + hexHashShort(digest.digest());
	}
	
	public void explain(Path cache) throws IOException {
		if(props.isEmpty()) return;
		
		Path explanationFile = cache.resolve("hash" + suffix() + "-explanation.txt");
		Files.deleteIfExists(explanationFile);
		
		try(PrintWriter out = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(explanationFile)))) {
			out.println("Properties rolled into hash '" + suffix() + "':");
			props.forEach((k, v) -> out.println(k + "=" + v));
		}
	}
	
	private static MessageDigest sha1() {
		try {
			return MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException what) {
			throw new RuntimeException("Someone's been tampering with the universe!", what);
		}
	}
	
	private String hexHashShort(byte[] result) {
		return hexHash(result, 4);
	}
	
	private String hexHash(byte[] result) {
		return hexHash(result, result.length);
	}
	
	private String hexHash(byte[] result, int bytesToUse) {
		StringBuilder out = new StringBuilder(bytesToUse * 2);
		for(int i = 0; i < bytesToUse; i++) {
			int hi = (result[i] & 0xF0) >> 4;
			int lo = result[i] & 0x0F;
			out.append((char) ((hi < 10 ? '0' : 'W') + hi)); //'W' == 'a'-10
			out.append((char) ((lo < 10 ? '0' : 'W') + lo));
		}
		
		return out.toString();
	}
}
