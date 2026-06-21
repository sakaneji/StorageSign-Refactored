package storagesign.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
