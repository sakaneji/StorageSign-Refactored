package storagesign.index;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

/** Versioned, checksummed binary codec for the persistent StorageSign index. */
public final class StorageSignIndexCodec {
    public static final int VERSION = 1;
    private static final int MAGIC = 0x53534958; // SSIX
    private static final int MAX_ENTRIES = 10_000_000;
    private static final int MAX_IDENTIFIER_BYTES = 65_536;

    public List<IndexedStorageSign> read(Path path) throws IOException {
        if (!Files.exists(path)) return List.of();
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 16) throw new IOException("Index file is truncated");
        int storedCrc = readTrailingInt(bytes);
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length - Integer.BYTES);
        if ((int) crc.getValue() != storedCrc) throw new IOException("Index CRC mismatch");

        try (DataInputStream input = new DataInputStream(
            new ByteArrayInputStream(bytes, 0, bytes.length - Integer.BYTES))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid index magic");
            int version = input.readInt();
            if (version != VERSION) throw new IOException("Unsupported index version: " + version);
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES) throw new IOException("Invalid index count: " + count);
            if (count > input.available() / 45) {
                throw new IOException("Index count exceeds available data: " + count);
            }
            List<IndexedStorageSign> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID world = new UUID(input.readLong(), input.readLong());
                int x = input.readInt();
                int y = input.readInt();
                int z = input.readInt();
                int amount = input.readInt();
                long verifiedAt = input.readLong();
                int identifierLength = input.readInt();
                if (identifierLength <= 0 || identifierLength > MAX_IDENTIFIER_BYTES
                    || identifierLength > input.available()) {
                    throw new IOException("Invalid identifier length: " + identifierLength);
                }
                String identifier = new String(input.readNBytes(identifierLength), StandardCharsets.UTF_8);
                entries.add(new IndexedStorageSign(
                    new StorageSignPosition(world, x, y, z), identifier, amount, verifiedAt));
            }
            if (input.available() != 0) throw new IOException("Unexpected trailing index data");
            return List.copyOf(entries);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid index entry", e);
        }
    }

    public long writeAtomic(Path path, List<IndexedStorageSign> entries) throws IOException {
        if (entries.size() > MAX_ENTRIES) throw new IOException("Too many index entries");
        Files.createDirectories(path.getParent());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(128, entries.size() * 48));
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(entries.size());
            for (IndexedStorageSign entry : entries) {
                StorageSignPosition position = entry.position();
                output.writeLong(position.worldId().getMostSignificantBits());
                output.writeLong(position.worldId().getLeastSignificantBits());
                output.writeInt(position.x());
                output.writeInt(position.y());
                output.writeInt(position.z());
                output.writeInt(entry.amount());
                output.writeLong(entry.verifiedAtEpochMillis());
                byte[] identifier = entry.identifier().getBytes(StandardCharsets.UTF_8);
                if (identifier.length == 0 || identifier.length > MAX_IDENTIFIER_BYTES) {
                    throw new IOException("Identifier is too long");
                }
                output.writeInt(identifier.length);
                output.write(identifier);
            }
        }
        byte[] payload = buffer.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
            output.write(payload);
            output.writeInt((int) crc.getValue());
            output.flush();
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
        return Files.size(path);
    }

    private static int readTrailingInt(byte[] bytes) {
        int offset = bytes.length - Integer.BYTES;
        return ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
    }
}
