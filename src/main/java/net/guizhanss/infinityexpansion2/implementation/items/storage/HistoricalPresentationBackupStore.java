package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * Durable per-owner backups for deliberately confirmed, main-hand,
 * presentation-only item repairs. Does not modify any item or world.
 *
 * <p>Records are written and forced to disk before a caller may change an item.
 * A separate, exact-match rollback requires the repaired ItemStack in hand.
 * Failed or incomplete writes never authorize an inventory mutation.
 */
public final class HistoricalPresentationBackupStore {
    private static final int MAGIC = 0x49453250; // "IE2P"
    private static final int FORMAT = 1;
    private static final int MAX_ITEM_BYTES = 16 * 1024 * 1024;
    private static final int MAX_RECORD_BYTES = MAX_ITEM_BYTES * 2 + 256;

    private HistoricalPresentationBackupStore() {}

    public record Backup(ItemStack original, ItemStack proposed) {}

    public static UUID save(Path base, UUID owner, ItemStack original, ItemStack proposed) throws IOException {
        if (base == null || owner == null || original == null || proposed == null) {
            throw new IOException("Missing backup owner or exact item pair");
        }
        // A backup must never silently change the quantity or the non-name data.
        if (original.getAmount() != proposed.getAmount()) {
            throw new IOException("Backup quantities differ");
        }
        // Defense in depth: never persist an authorization record for an item
        // whose typed ledger, owner PDC, model or any non-presentation component
        // changed. The preview already checks this, but backup is the final
        // pre-write boundary and must enforce it independently.
        if (!HistoricalPresentationPreview.sameNonPresentation(original, proposed)) {
            throw new IOException("Backup item differs outside presentation");
        }
        var originalBytes = original.serializeAsBytes();
        var proposedBytes = proposed.serializeAsBytes();
        if (originalBytes.length == 0 || proposedBytes.length == 0
                || originalBytes.length > MAX_ITEM_BYTES || proposedBytes.length > MAX_ITEM_BYTES) {
            throw new IOException("Serialized item exceeds bounded backup size");
        }
        var id = UUID.randomUUID();
        Path directory = ownerDir(base, owner, true);
        Path finalFile = directory.resolve(id + ".bin");
        if (Files.exists(finalFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Backup ID already used");
        }

        byte[] payload;
        try (var bytes = new ByteArrayOutputStream();
             var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeLong(owner.getMostSignificantBits());
            out.writeLong(owner.getLeastSignificantBits());
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
            out.writeInt(originalBytes.length);
            out.write(originalBytes);
            out.writeInt(proposedBytes.length);
            out.write(proposedBytes);
            out.write(digest(originalBytes, proposedBytes));
            out.flush();
            payload = bytes.toByteArray();
        }
        Path temp = Files.createTempFile(directory, "pending-", ".tmp");
        try {
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                var buffer = ByteBuffer.wrap(payload);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temp, finalFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("Atomic item backup is required", unsupported);
            }
            forceDirectory(directory);
            // Exact readback is mandatory before any caller can use the backup.
            Backup readback = load(base, owner, id);
            if (!original.equals(readback.original()) || !proposed.equals(readback.proposed())) {
                throw new IOException("Durable backup readback did not match the exact item pair");
            }
            return id;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static Backup load(Path base, UUID owner, UUID id) throws IOException {
        if (id == null) throw new IOException("Missing backup ID");
        Path directory = ownerDir(base, owner, false);
        Path file = directory.resolve(id + ".bin");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > MAX_RECORD_BYTES) {
            throw new IOException("Missing or unsafe item backup file");
        }
        byte[] bytes;
        try (var in = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            bytes = in.readAllBytes();
        }
        if (bytes.length > MAX_RECORD_BYTES) throw new IOException("Oversized item backup");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT
                    || in.readLong() != owner.getMostSignificantBits()
                    || in.readLong() != owner.getLeastSignificantBits()
                    || in.readLong() != id.getMostSignificantBits()
                    || in.readLong() != id.getLeastSignificantBits()) {
                throw new IOException("Backup magic, version or owner identity mismatch");
            }
            byte[] oldBytes = readItemBytes(in);
            byte[] newBytes = readItemBytes(in);
            byte[] hash = new byte[32];
            in.readFully(hash);
            if (in.available() != 0 || !MessageDigest.isEqual(hash, digest(oldBytes, newBytes))) {
                throw new IOException("Backup integrity digest mismatch");
            }
            // Never tolerate corrupt serialized metadata or downgraded typed PDC.
            var original = ItemStack.deserializeBytes(oldBytes);
            var proposed = ItemStack.deserializeBytes(newBytes);
            if (original == null || proposed == null
                    || !Arrays.equals(oldBytes, original.serializeAsBytes())
                    || !Arrays.equals(newBytes, proposed.serializeAsBytes())
                    || original.getAmount() != proposed.getAmount()
                    || !HistoricalPresentationPreview.sameNonPresentation(original, proposed)) {
                // The digest detects corruption, but must not be treated as
                // permission to restore a record with changed typed PDC or
                // any non-presentation components (including an old record).
                throw new IOException("Serialized backup item metadata is not safe");
            }
            return new Backup(original, proposed);
        } catch (RuntimeException invalid) {
            throw new IOException("Unreadable saved ItemStack metadata", invalid);
        }
    }

    public static void markRolledBack(Path base, UUID owner, UUID id) throws IOException {
        Path directory = ownerDir(base, owner, false);
        Path source = directory.resolve(id + ".bin");
        Path retired = directory.resolve(id + ".rolledback");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(retired, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Backup already consumed or unavailable");
        }
        // Retain original evidence as a non-replayable audit file.
        Files.move(source, retired, StandardCopyOption.ATOMIC_MOVE);
        forceDirectory(directory);
    }

    private static byte[] readItemBytes(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n <= 0 || n > MAX_ITEM_BYTES || n > in.available()) {
            throw new IOException("Invalid backup item length");
        }
        byte[] result = new byte[n];
        in.readFully(result);
        return result;
    }

    private static Path ownerDir(Path base, UUID owner, boolean create) throws IOException {
        if (base == null || owner == null) throw new IOException("Invalid backup directory/owner");
        if (Files.isSymbolicLink(base)) throw new IOException("Backup directory is a symlink");
        if (create) Files.createDirectories(base);
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Backup directory missing or linked");
        }
        Path dir = base.resolve(owner.toString());
        if (create) Files.createDirectories(dir);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owner backup directory missing or linked");
        }
        return dir;
    }

    private static byte[] digest(byte[] original, byte[] proposed) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(original);
            hash.update(proposed);
            return hash.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }
}
