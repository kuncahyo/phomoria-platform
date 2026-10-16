package com.phomoria.print.windows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Optional;

/** Stores driver-owned DEVMODE bytes separately for Profile 1 and Profile 2. */
public final class PrinterProfileStore {
    private final Path directory;

    public PrinterProfileStore() {
        this(Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
                "Phomoria", "printer-profiles"));
    }

    public PrinterProfileStore(Path directory) {
        this.directory = directory;
    }

    public void save(PrinterProfileId id, PrinterProfileData data) throws IOException {
        Files.createDirectories(directory);
        String encoded = Base64.getEncoder().encodeToString(data.getDevMode());
        String content = "PHOMORIA_DEVMODE_V1\n" + Base64.getEncoder().encodeToString(
                data.getPrinterName().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "\n" + encoded;
        Path target = file(id);
        Path temporary = Files.createTempFile(directory, id.fileKey(), ".tmp");
        try {
            Files.writeString(temporary, content, java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Optional<PrinterProfileData> load(PrinterProfileId id) throws IOException {
        Path path = file(id);
        if (!Files.isRegularFile(path)) return Optional.empty();
        String[] parts = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8).split("\\R", 3);
        if (parts.length != 3 || !"PHOMORIA_DEVMODE_V1".equals(parts[0])) {
            throw new IOException("Unsupported or damaged printer profile: " + path);
        }
        try {
            String printer = new String(Base64.getDecoder().decode(parts[1]),
                    java.nio.charset.StandardCharsets.UTF_8);
            byte[] devMode = Base64.getDecoder().decode(parts[2]);
            return Optional.of(new PrinterProfileData(printer, devMode));
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid printer profile data: " + path, ex);
        }
    }

    public void delete(PrinterProfileId id) throws IOException {
        Files.deleteIfExists(file(id));
    }

    private Path file(PrinterProfileId id) {
        return directory.resolve(id.fileKey() + ".devmode");
    }
}
