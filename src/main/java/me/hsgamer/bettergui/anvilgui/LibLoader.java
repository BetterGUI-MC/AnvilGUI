package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.hscore.logger.common.Logger;
import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import net.byteflux.libby.classloader.URLClassLoaderHelper;
import net.byteflux.libby.logging.LogLevel;
import net.byteflux.libby.logging.adapters.LogAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class LibLoader extends LibraryManager {
    private final Function<List<Path>, List<Path>> REMAPPER;
    private final URLClassLoaderHelper classLoader;
    private final Main main;

    public LibLoader(Main main) {
        super(new LogAdapter() {
            @Override
            public void log(LogLevel level, String message) {
                Logger logger = main.getLogger();
                switch (level) {
                    case DEBUG:
                        logger.log(me.hsgamer.hscore.logger.common.LogLevel.DEBUG, message);
                        break;
                    case INFO:
                        logger.log(me.hsgamer.hscore.logger.common.LogLevel.INFO, message);
                        break;
                    case WARN:
                        logger.log(me.hsgamer.hscore.logger.common.LogLevel.WARN, message);
                        break;
                    case ERROR:
                        logger.log(me.hsgamer.hscore.logger.common.LogLevel.ERROR, message);
                        break;
                }
            }

            @Override
            public void log(LogLevel level, String message, Throwable throwable) {
                Logger logger = main.getLogger();
                switch (level) {
                    case DEBUG:
                        logger.log(me.hsgamer.hscore.logger.common.LogLevel.DEBUG, message, throwable);
                        break;
                    case INFO:
                        logger.log(me.hsgamer.hscore.logger.common.LogLevel.INFO, message, throwable);
                        break;
                    case WARN:
                        logger.log(me.hsgamer.hscore.logger.common.LogLevel.WARN, message, throwable);
                        break;
                    case ERROR:
                        logger.log(me.hsgamer.hscore.logger.common.LogLevel.ERROR, message, throwable);
                        break;
                }
            }
        }, main.getDataFolder().toPath(), "lib");
        this.classLoader = new URLClassLoaderHelper(main.getExpansionClassLoader(), this);
        this.main = main;

        Function<List<Path>, List<Path>> remapper;
        try {
            Class<?> clazz = Class.forName("org.bukkit.plugin.java.LibraryLoader");
            Field remapperField = clazz.getDeclaredField("REMAPPER");

            //noinspection unchecked
            remapper = (Function<List<Path>, List<Path>>) remapperField.get(null);
        } catch (Exception e) {
            remapper = null;
        }
        REMAPPER = remapper;
    }

    @Override
    protected void addToClasspath(Path file) {
        classLoader.addToClasspath(file);
    }

    private Path remap(Path file) {
        return REMAPPER != null ? REMAPPER.apply(Collections.singletonList(file)).get(0) : file;
    }

    // This method's only purpose is to add the "paperweight-mappings-namespace" attribute to the MANIFEST.MF file of the library
    // So that it can be remapped by Paper's PluginRemapper
    // Thanks Copilot for this monstrosity of a method that solves the problem
    private void modifyManifest(Path file, Path modifiedFile) throws IOException {
        Path tempJarPath = Files.createTempFile("temp-", ".jar");

        try (JarFile jarFile = new JarFile(file.toFile());
             JarOutputStream tempJarOutputStream = new JarOutputStream(Files.newOutputStream(tempJarPath))) {

            // Copy existing entries
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (JarFile.MANIFEST_NAME.equals(entry.getName())) {
                    continue; // Skip the existing MANIFEST.MF
                }
                try (InputStream entryInputStream = jarFile.getInputStream(entry)) {
                    tempJarOutputStream.putNextEntry(new JarEntry(entry.getName()));
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = entryInputStream.read(buffer)) != -1) {
                        tempJarOutputStream.write(buffer, 0, bytesRead);
                    }
                    tempJarOutputStream.closeEntry();
                }
            }

            // Modify the MANIFEST.MF
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                manifest = new Manifest();
            }
            manifest.getMainAttributes().putValue("paperweight-mappings-namespace", "spigot");

            // Write the modified MANIFEST.MF
            tempJarOutputStream.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
            manifest.write(tempJarOutputStream);
            tempJarOutputStream.closeEntry();
        }

        Files.move(tempJarPath, modifiedFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public void loadLibrary() throws Exception {
        try (
                InputStream inputStream = Objects.requireNonNull(main.getExpansionClassLoader().getResourceAsStream("LIB_VERSION"));
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))
        ) {
            String version = reader.readLine();
            Library lib = Library.builder()
                    .groupId("net{}wesjd")
                    .artifactId("anvilgui")
                    .version(version)
                    .repository("https://repo.codemc.io/repository/maven-public/")
                    .build();


            Path path = saveDirectory.resolve(lib.getPath());
            Path parent = path.getParent();
            String name = path.getFileName().toString();
            name = name.substring(0, name.lastIndexOf('.'));

            boolean downloaded = false;
            if (!Files.exists(path)) {
                downloadLibrary(lib);
                downloaded = true;
            }

            Path modifiedPath = parent.resolve(name + "-modified.jar");
            if (downloaded || !Files.exists(modifiedPath)) {
                modifyManifest(path, modifiedPath);
            }

            Path remappedPath = remap(modifiedPath);
            addToClasspath(remappedPath);
        }
    }
}
