package storagesign.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class StorageSignIndexCodecTest {
    @TempDir Path temporary;

    @Test
    void roundTripsMultipleWorldsNegativeCoordinatesAndAmounts() throws Exception {
        UUID firstWorld = UUID.randomUUID();
        UUID secondWorld = UUID.randomUUID();
        List<IndexedStorageSign> expected = List.of(
            new IndexedStorageSign(new StorageSignPosition(firstWorld, -123, -64, 456),
                "NETHERITE_UPGRADE_SMITHING_TEMPLATE", Integer.MAX_VALUE, 123456789L),
            new IndexedStorageSign(new StorageSignPosition(secondWorld, 0, 319, 0),
                "STONE", 0, 987654321L));
        Path file = temporary.resolve("storage-sign-index.bin");
        StorageSignIndexCodec codec = new StorageSignIndexCodec();

        codec.writeAtomic(file, expected);

        assertEquals(expected, codec.read(file));
    }

    @Test
    void rejectsTruncatedAndChecksumDamagedFiles() throws Exception {
        Path file = temporary.resolve("storage-sign-index.bin");
        StorageSignIndexCodec codec = new StorageSignIndexCodec();
        codec.writeAtomic(file, List.of(new IndexedStorageSign(
            new StorageSignPosition(UUID.randomUUID(), 1, 2, 3), "STONE", 4, 5)));
        byte[] bytes = Files.readAllBytes(file);
        bytes[12] ^= 0x01;
        Files.write(file, bytes);

        assertThrows(IOException.class, () -> codec.read(file));
        Files.write(file, new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> codec.read(file));
    }

    @Test
    void missingFileLoadsAsEmptyIndex() throws Exception {
        assertEquals(List.of(), new StorageSignIndexCodec().read(temporary.resolve("missing.bin")));
    }

    @Test
    void rejectsInvalidMagicVersionCountIdentifierUtf8AndTrailingData() throws Exception {
        StorageSignIndexCodec codec = new StorageSignIndexCodec();
        assertRejected(codec, payload(0, 1, 0), "magic");
        assertRejected(codec, payload(0x53534958, 2, 0), "version");
        assertRejected(codec, payload(0x53534958, 1, -1), "count");
        assertRejected(codec, payload(0x53534958, 1, 10_000_001), "count");
        assertRejected(codec, payload(0x53534958, 1, 1), "count");

        ByteArrayOutputStream trailing = payload(0x53534958, 1, 0);
        trailing.write(1);
        assertRejected(codec, trailing, "trailing");

        ByteArrayOutputStream invalidUtf8 = payload(0x53534958, 1, 1);
        try (DataOutputStream output = new DataOutputStream(invalidUtf8)) {
            output.writeLong(0); output.writeLong(0);
            output.writeInt(0); output.writeInt(0); output.writeInt(0); output.writeInt(1);
            output.writeLong(0); output.writeInt(1); output.writeByte(0xff);
        }
        assertRejected(codec, invalidUtf8, "UTF-8");

        ByteArrayOutputStream invalidIdentifierLength = payload(0x53534958, 1, 1);
        try (DataOutputStream output = new DataOutputStream(invalidIdentifierLength)) {
            output.writeLong(0); output.writeLong(0);
            output.writeInt(0); output.writeInt(0); output.writeInt(0); output.writeInt(1);
            output.writeLong(0); output.writeInt(65_537); output.writeByte(0);
        }
        assertRejected(codec, invalidIdentifierLength, "Invalid identifier length");

        ByteArrayOutputStream missingIdentifierBytes = payload(0x53534958, 1, 1);
        try (DataOutputStream output = new DataOutputStream(missingIdentifierBytes)) {
            output.writeLong(0); output.writeLong(0);
            output.writeInt(0); output.writeInt(0); output.writeInt(0); output.writeInt(1);
            output.writeLong(0); output.writeInt(2); output.writeByte(0);
        }
        assertRejected(codec, missingIdentifierBytes, "Invalid identifier length");

        ByteArrayOutputStream emptyIdentifier = payload(0x53534958, 1, 1);
        try (DataOutputStream output = new DataOutputStream(emptyIdentifier)) {
            output.writeLong(0); output.writeLong(0);
            output.writeInt(0); output.writeInt(0); output.writeInt(0); output.writeInt(1);
            output.writeLong(0); output.writeInt(0); output.writeByte(0);
        }
        assertRejected(codec, emptyIdentifier, "Invalid identifier length");

        ByteArrayOutputStream negativeAmount = payload(0x53534958, 1, 1);
        try (DataOutputStream output = new DataOutputStream(negativeAmount)) {
            output.writeLong(0); output.writeLong(0);
            output.writeInt(0); output.writeInt(0); output.writeInt(0); output.writeInt(-1);
            output.writeLong(0); output.writeInt(1); output.writeByte('S');
        }
        assertRejected(codec, negativeAmount, "Invalid index entry");
    }

    @Test
    void readsProtocolGoldenFileSharedWithPythonTool() throws Exception {
        UUID world = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        Path file = temporary.resolve("python-compatible.bin");
        String encoded = Files.readString(
            Path.of("tools/tests/fixtures/storage-sign-index-v1.base64"), StandardCharsets.US_ASCII).trim();
        Files.write(file, Base64.getDecoder().decode(encoded));

        assertEquals(List.of(new IndexedStorageSign(
            new StorageSignPosition(world, -10, 64, 30), "STONE", 128, 1_700_000_000_000L)),
            new StorageSignIndexCodec().read(file));
    }

    @Test
    void failedReplacementLeavesPreviousValidFileReadable() throws Exception {
        Path file = temporary.resolve("storage-sign-index.bin");
        StorageSignIndexCodec codec = new StorageSignIndexCodec();
        IndexedStorageSign valid = new IndexedStorageSign(
            new StorageSignPosition(UUID.randomUUID(), 1, 2, 3), "STONE", 4, 5);
        codec.writeAtomic(file, List.of(valid));
        IndexedStorageSign oversized = new IndexedStorageSign(valid.position(), "X".repeat(65_537), 4, 5);

        assertThrows(IOException.class, () -> codec.writeAtomic(file, List.of(oversized)));

        assertEquals(List.of(valid), codec.read(file));
    }

    @Test
    void writeAtomicFallsBackWhenAtomicMoveIsNotSupported() throws Exception {
        Path file = temporary.resolve("storage-sign-index.bin");
        StorageSignIndexCodec codec = new StorageSignIndexCodec();
        IndexedStorageSign entry = new IndexedStorageSign(
            new StorageSignPosition(UUID.randomUUID(), 1, 2, 3), "STONE", 4, 5);

        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(
                    Mockito.any(Path.class), Mockito.any(Path.class),
                    Mockito.eq(java.nio.file.StandardCopyOption.ATOMIC_MOVE),
                    Mockito.eq(java.nio.file.StandardCopyOption.REPLACE_EXISTING)))
                .thenThrow(new AtomicMoveNotSupportedException(file.toString(), null, "no atomic move"));

            long size = codec.writeAtomic(file, List.of(entry));

            assertTrue(size > 0);
            files.verify(() -> Files.move(
                Mockito.any(Path.class), Mockito.any(Path.class),
                Mockito.eq(java.nio.file.StandardCopyOption.ATOMIC_MOVE),
                Mockito.eq(java.nio.file.StandardCopyOption.REPLACE_EXISTING)));
            files.verify(() -> Files.move(
                Mockito.any(Path.class), Mockito.any(Path.class),
                Mockito.eq(java.nio.file.StandardCopyOption.REPLACE_EXISTING)));
        }
    }

    @Test
    void writeAtomicRejectsEntriesPastMaximumAndEmptyIdentifiers() throws Exception {
        StorageSignIndexCodec codec = new StorageSignIndexCodec();
        Path file = temporary.resolve("storage-sign-index.bin");
        IndexedStorageSign valid = new IndexedStorageSign(
            new StorageSignPosition(UUID.randomUUID(), 1, 2, 3), "STONE", 4, 5);
        List<IndexedStorageSign> tooMany = new java.util.AbstractList<>() {
            @Override public IndexedStorageSign get(int index) { return valid; }
            @Override public int size() { return 10_000_001; }
        };

        assertThrows(IOException.class, () -> codec.writeAtomic(file, tooMany));
    }

    @Test
    void writeAtomicRejectsOversizedIdentifiers() throws Exception {
        StorageSignIndexCodec codec = new StorageSignIndexCodec();
        Path file = temporary.resolve("storage-sign-index.bin");
        IndexedStorageSign oversized = new IndexedStorageSign(
            new StorageSignPosition(UUID.randomUUID(), 1, 2, 3), "X".repeat(65_537), 4, 5);

        assertThrows(IOException.class, () -> codec.writeAtomic(file, List.of(oversized)));
    }

    @Test
    void validateIdentifierBytesRejectsEmptyAndOversizedArrays() throws Exception {
        Method method = StorageSignIndexCodec.class.getDeclaredMethod(
            "validateIdentifierBytes", byte[].class);
        method.setAccessible(true);

        assertThrows(IOException.class, () -> invokeIdentifierValidator(method, new byte[0]));
        assertThrows(IOException.class, () -> invokeIdentifierValidator(method, new byte[65_537]));
        assertDoesNotThrow(() -> method.invoke(null, new byte[] {1}));
    }

    private void assertRejected(StorageSignIndexCodec codec, ByteArrayOutputStream content,
                                String expectedMessage) throws Exception {
        Path file = temporary.resolve("invalid.bin");
        writeChecksummed(file, content.toByteArray());
        IOException error = assertThrows(IOException.class, () -> codec.read(file));
        assertTrue(error.getMessage().toLowerCase().contains(expectedMessage.toLowerCase()), error::getMessage);
    }

    private static ByteArrayOutputStream payload(int magic, int version, int count) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(content);
        output.writeInt(magic); output.writeInt(version); output.writeInt(count);
        return content;
    }

    private static void writeChecksummed(Path file, byte[] content) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(content);
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file))) {
            output.write(content); output.writeInt((int) crc.getValue());
        }
    }

    private static void invokeIdentifierValidator(Method method, byte[] value) throws IOException {
        try {
            method.invoke(null, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new AssertionError(e.getCause());
        }
    }
}
