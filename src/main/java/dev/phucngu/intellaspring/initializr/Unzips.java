package dev.phucngu.intellaspring.initializr;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Zip extraction with a zip-slip guard — entries must stay inside the target directory. */
public final class Unzips {

    private Unzips() {
    }

    public static void unzip(byte @NotNull [] zipBytes, @NotNull Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = normalizedTarget.resolve(entry.getName()).normalize();
                if (!target.startsWith(normalizedTarget)) {
                    continue; // refuse to write outside the project directory
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target)) {
                        zip.transferTo(out);
                    }
                }
            }
        }
    }
}
