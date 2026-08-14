package dev.fabricmultiloader.format.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Sha256Test {

    @Test
    @DisplayName("matches the published NIST vectors")
    void knownVectors() {
        assertThat(Sha256.ofUtf8(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(Sha256.ofUtf8("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Sha256.ofUtf8("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"))
                .isEqualTo("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1");
    }

    @Test
    @DisplayName("streaming a large input agrees with hashing it in one go")
    void streamingMatchesOneShot() throws Exception {
        byte[] data = new byte[512 * 1024 + 7];
        new Random(20260811L).nextBytes(data);

        String streamed = Sha256.of(new ByteArrayInputStream(data));
        String oneShot = Sha256.of(data);
        String reference = toHex(MessageDigest.getInstance("SHA-256").digest(data));

        assertThat(streamed).isEqualTo(oneShot).isEqualTo(reference);
    }

    @Test
    void producesLowerCaseHexOfTheRightLength() {
        String digest = Sha256.ofUtf8("payload");
        assertThat(digest).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(Sha256.isValidDigest(digest)).isTrue();
    }

    @Test
    @DisplayName("comparison is case-insensitive and length-checked")
    void comparesDigests() {
        String digest = Sha256.ofUtf8("payload");
        assertThat(Sha256.matches(digest, digest)).isTrue();
        assertThat(Sha256.matches(digest, digest.toUpperCase(java.util.Locale.ROOT))).isTrue();
        assertThat(Sha256.matches(digest, Sha256.ofUtf8("other"))).isFalse();
        assertThat(Sha256.matches(digest, digest.substring(1))).isFalse();
        assertThat(Sha256.matches(null, digest)).isFalse();
        assertThat(Sha256.matches(digest, null)).isFalse();
    }

    @Test
    void rejectsMalformedDigests() {
        assertThat(Sha256.isValidDigest(null)).isFalse();
        assertThat(Sha256.isValidDigest("")).isFalse();
        assertThat(Sha256.isValidDigest("abc")).isFalse();
        assertThat(Sha256.isValidDigest(repeat("g", 64))).isFalse();
        assertThat(Sha256.isValidDigest(repeat("a", 63))).isFalse();
        assertThat(Sha256.isValidDigest(repeat("ab", 32))).isTrue();
        // Upper case hex is accepted: some tools emit it, and matches() is case-insensitive.
        assertThat(Sha256.isValidDigest(repeat("A", 64))).isTrue();
    }

    @Test
    void rejectsNullStreams() {
        assertThatThrownBy(() -> Sha256.of((java.io.InputStream) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hashing is encoding-stable, so a digest computed on Windows matches Linux")
    void usesUtf8Regardless() {
        String withUmlauts = "Grüße 🎮";
        assertThat(Sha256.ofUtf8(withUmlauts))
                .isEqualTo(Sha256.of(withUmlauts.getBytes(Charset.forName("UTF-8"))));
    }

    /** Java 8 has no String#repeat, and these modules deliberately stay on the Java 8 baseline. */
    private static String repeat(String text, int times) {
        StringBuilder out = new StringBuilder(text.length() * times);
        for (int i = 0; i < times; i++) {
            out.append(text);
        }
        return out.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }
}
