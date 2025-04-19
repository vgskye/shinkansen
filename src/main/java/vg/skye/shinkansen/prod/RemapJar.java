package vg.skye.shinkansen.prod;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.format.srg.TsrgFileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.jvm.tasks.Jar;
import vg.skye.shinkansen.ShinkansenPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class RemapJar extends Jar {
    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    @InputFiles
    public abstract ConfigurableFileCollection getMappings();

    @InputFile
    public abstract RegularFileProperty getInput();

    @Input
    public abstract Property<String> getSourceMapping();

    @Input
    public abstract Property<String> getDestMapping();

    @Override
    protected void copy() {
    }

    @TaskAction
    protected void remap() throws IOException {
        Logger logger = getProject().getLogger();
        logger.info("Remapping!");
        MemoryMappingTree mappingTree = new MemoryMappingTree();
        for (File mapping : getMappings()) {
            logger.info("Loading mapping file " + mapping.toString());
            if (mapping.getName().endsWith(".txt")) {
                ProGuardFileReader.read(Files.newBufferedReader(mapping.toPath()), "named", "obf", mappingTree);
                mappingTree.reset();
            } else {
                try (FileSystem mappingFs = ShinkansenPlugin.openFs(mapping.toPath())) {
                    Files.walkFileTree(mappingFs.getPath("/"), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
                            if (attr.isDirectory()) {
                                return FileVisitResult.CONTINUE;
                            }

                            if (path.getFileName().toString().endsWith(".tiny")) {
                                logger.info("Loading Tiny mapping file " + path);
                                HashMap<String, String> nameMap = new HashMap<>();
                                nameMap.put("official", "obf");
                                MappingReader.read(path, new MappingNsRenamer(mappingTree, nameMap));
                                mappingTree.reset();
                            }
                            if (path.getFileName().toString().endsWith(".tsrg")) {
                                logger.info("Loading TSRG mapping file " + path);
                                TsrgFileReader.read(Files.newBufferedReader(path), mappingTree);
                                mappingTree.reset();
                            }

                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
        }
        if (mappingTree.getDstNamespaces().contains("srg") && (mappingTree.getDstNamespaces().contains("named") || mappingTree.getSrcNamespace().equals("named"))) {
            logger.info("Adding virtual-srg");
            List<String> namespaces = new ArrayList<>();
            namespaces.add("virtual-srg");
            mappingTree.visitNamespaces(mappingTree.getSrcNamespace(), namespaces);
            int srgId = mappingTree.getNamespaceId("srg");
            for (MappingTree.ClassMapping classMapping : mappingTree.getClasses()) {
                mappingTree.visitClass(classMapping.getSrcName());
                mappingTree.visitDstName(MappedElementKind.CLASS, 0, classMapping.getName("named"));
                for (MappingTree.MethodMapping methodMapping : classMapping.getMethods()) {
                    mappingTree.visitMethod(methodMapping.getSrcName(), methodMapping.getSrcDesc());
                    mappingTree.visitDstName(MappedElementKind.METHOD, 0, methodMapping.getDstName(srgId));
                    mappingTree.visitDstDesc(MappedElementKind.METHOD, 0, methodMapping.getDstDesc(srgId));
                    for (MappingTree.MethodArgMapping argMapping : methodMapping.getArgs()) {
                        mappingTree.visitMethodArg(argMapping.getArgPosition(), argMapping.getLvIndex(), argMapping.getSrcName());
                        mappingTree.visitDstName(MappedElementKind.METHOD_ARG, 0, argMapping.getDstName(srgId));
                    }
                    for (MappingTree.MethodVarMapping varMapping : methodMapping.getVars()) {
                        mappingTree.visitMethodVar(varMapping.getLvtRowIndex(), varMapping.getLvIndex(), varMapping.getStartOpIdx(), varMapping.getEndOpIdx(), varMapping.getSrcName());
                        mappingTree.visitDstName(MappedElementKind.METHOD_VAR, 0, varMapping.getDstName(srgId));
                    }
                }
                for (MappingTree.FieldMapping fieldMapping : classMapping.getFields()) {
                    mappingTree.visitField(fieldMapping.getSrcName(), fieldMapping.getSrcDesc());
                    mappingTree.visitDstName(MappedElementKind.FIELD, 0, fieldMapping.getDstName(srgId));
                    mappingTree.visitDstDesc(MappedElementKind.FIELD, 0, fieldMapping.getDstDesc(srgId));
                }
            }
            mappingTree.visitEnd();
            mappingTree.reset();
        }
        TinyRemapper remapper = TinyRemapper
                .newRemapper()
                .withMappings(TinyUtils.createMappingProvider(mappingTree, getSourceMapping().get(), getDestMapping().get()))
                .build();
        for (File library : getLibraries()) {
            remapper.readClassPath(library.toPath());
        }
        remapper.readInputs(getInput().get().getAsFile().toPath());
        try(OutputConsumerPath oc = new OutputConsumerPath.Builder(getArchiveFile().get().getAsFile().toPath()).assumeArchive(true).build()) {
            oc.addNonClassFiles(getInput().get().getAsFile().toPath());
            remapper.apply(oc);
        } finally {
            remapper.finish();
        }
    }
}
