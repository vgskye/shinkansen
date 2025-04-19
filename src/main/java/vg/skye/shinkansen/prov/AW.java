package vg.skye.shinkansen.prov;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
import net.fabricmc.accesswidener.AccessWidenerReader;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class AW extends MiniProvider {
	public AW(Project project, Path inJar, String outJarName, List<Path> widenerFiles) throws IOException {
		super(project);
		
		this.inJar = inJar;
		this.outJarName = outJarName;
		this.widenerFiles = widenerFiles;
		
		//Put access widened products in a local cache directory
		props.set("projectmapped", "t");
		
		//roll the access widener file hashes inside
		int i = 1;
		for(Path p : widenerFiles) {
			props.setFile("access-widener-" + i++, p);
		}
	}
	
	private final Path inJar;
	private final String outJarName;
	private final List<Path> widenerFiles;
	
	public Path widen() throws Exception {
		Path result = getOrCreate(subst(outJarName), outJar -> {
			log.lifecycle("Widening {} to {}...", inJar, outJar);
			
			AccessWidener aw = new AccessWidener();
			
			log.lifecycle("\\-> Parsing {} access widener file{}.", widenerFiles.size(), widenerFiles.size() == 1 ? "" : "s");
			for(Path awPath : widenerFiles) {
				log.lifecycle("  \\-> Reading {}...", awPath);
				AccessWidenerReader awReader = new AccessWidenerReader(aw);
				try(BufferedReader reader = Files.newBufferedReader(awPath)) {
					awReader.read(reader);
				}
			}
			
			log.lifecycle("\\-> Widening...");
			try(
				ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(inJar)));
				ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outJar)));
			) {
				ZipEntry entry;
				while((entry = zin.getNextEntry()) != null) {
					if(entry.isDirectory()) continue;
					
					String name = entry.getName();
					
					zout.putNextEntry(entry);
					
					if(name.endsWith(".class")) {
						ClassReader classReader = new ClassReader(zin);
						ClassWriter classWriter = new ClassWriter(0);
						
						ClassVisitor awcv = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, classWriter, aw);
						classReader.accept(awcv, 0);
						
						zout.write(classWriter.toByteArray());
					} else {
						cpy(zin, zout);
					}
					
					zout.closeEntry();
				}
			}
			
			log.lifecycle("\\-> Done.");
		});
		
		log.info("widened: {}", result);
		return result;
	}
	
	private static void cpy(InputStream in, OutputStream out) throws IOException {
		byte[] shuttle = new byte[4096];
		int read;
		while((read = in.read(shuttle)) != -1) out.write(shuttle, 0, read);
	}
}
