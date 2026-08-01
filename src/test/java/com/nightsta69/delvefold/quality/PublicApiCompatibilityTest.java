package com.nightsta69.delvefold.quality;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that the public API-v1 members remain binary compatible with Delvefold 1.3.0. */
class PublicApiCompatibilityTest {
    private static final String BASELINE = "/quality/delvefold-api-v1.signatures";

    @Test
    void everyApiV1JvmDescriptorRemainsPublicAndResolvable() throws Exception {
        List<String> signatures;
        try (var stream = PublicApiCompatibilityTest.class.getResourceAsStream(BASELINE)) {
            assertNotNull(stream, "Missing checked-in API-v1 descriptor baseline");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                signatures = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .toList();
            }
        }

        assertTrue(!signatures.isEmpty(), "The API-v1 descriptor baseline must not be empty");
        assertTrue(signatures.stream().distinct().count() == signatures.size(),
                "The API-v1 descriptor baseline must not contain duplicate entries");
        for (String signature : signatures) {
            verify(signature);
        }
    }

    private static void verify(String signature) throws Exception {
        String[] parts = signature.split("\\|", -1);
        assertTrue(parts.length == 4, "Malformed API descriptor baseline entry: " + signature);

        String kind = parts[0];
        ClassContract owner = readClass(parts[1]);
        assertTrue(Modifier.isPublic(owner.access()), "API owner is no longer public: " + parts[1]);

        switch (kind) {
            case "FIELD" -> verifyField(owner, parts[2], parts[3], signature);
            case "METHOD" -> verifyMethod(owner, parts[2], parts[3], signature);
            case "CONSTRUCTOR" -> verifyConstructor(owner, parts[3], signature);
            default -> throw new AssertionError("Unknown API descriptor kind: " + signature);
        }
    }

    private static void verifyField(
            ClassContract owner, String name, String expectedDescriptor, String signature) {
        MemberContract match = owner.fields().stream()
                .filter(member -> member.name().equals(name) && member.descriptor().equals(expectedDescriptor))
                .findFirst()
                .orElse(null);
        assertNotNull(match, "Missing API field or changed JVM descriptor: " + signature);
        assertTrue(Modifier.isPublic(match.access()), "API field is no longer public: " + signature);
    }

    private static void verifyMethod(
            ClassContract owner, String name, String expectedDescriptor, String signature) {
        MemberContract match = owner.methods().stream()
                .filter(member -> member.name().equals(name) && member.descriptor().equals(expectedDescriptor))
                .findFirst()
                .orElse(null);
        assertNotNull(match, "Missing API method or changed JVM descriptor: " + signature);
        assertTrue(Modifier.isPublic(match.access()), "API method is no longer public: " + signature);
    }

    private static void verifyConstructor(ClassContract owner, String expectedDescriptor, String signature) {
        MemberContract match = owner.methods().stream()
                .filter(member -> member.name().equals("<init>")
                        && member.descriptor().equals(expectedDescriptor))
                .findFirst()
                .orElse(null);
        assertNotNull(match, "Missing API constructor or changed JVM descriptor: " + signature);
        assertTrue(Modifier.isPublic(match.access()), "API constructor is no longer public: " + signature);
    }

    private static ClassContract readClass(String binaryName) throws IOException {
        String resource = "/" + binaryName.replace('.', '/') + ".class";
        try (var raw = PublicApiCompatibilityTest.class.getResourceAsStream(resource)) {
            assertNotNull(raw, "Missing compiled API class: " + binaryName);
            try (var input = new DataInputStream(raw)) {
                assertTrue(input.readInt() == 0xCAFEBABE, "Invalid class-file header for " + binaryName);
                input.readUnsignedShort();
                input.readUnsignedShort();
                String[] utf8 = readConstantPool(input);
                int access = input.readUnsignedShort();
                input.readUnsignedShort();
                input.readUnsignedShort();
                skipInterfaces(input);
                List<MemberContract> fields = readMembers(input, utf8);
                List<MemberContract> methods = readMembers(input, utf8);
                return new ClassContract(access, fields, methods);
            }
        }
    }

    private static String[] readConstantPool(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        String[] utf8 = new String[count];
        for (int index = 1; index < count; index++) {
            int tag = input.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[index] = input.readUTF();
                case 3, 4 -> input.skipNBytes(4);
                case 5, 6 -> {
                    input.skipNBytes(8);
                    index++;
                }
                case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                case 15 -> input.skipNBytes(3);
                default -> throw new IOException("Unsupported class-file constant-pool tag " + tag);
            }
        }
        return utf8;
    }

    private static void skipInterfaces(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        input.skipNBytes(count * 2L);
    }

    private static List<MemberContract> readMembers(DataInputStream input, String[] utf8) throws IOException {
        int count = input.readUnsignedShort();
        List<MemberContract> members = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int access = input.readUnsignedShort();
            String name = utf8[input.readUnsignedShort()];
            String descriptor = utf8[input.readUnsignedShort()];
            members.add(new MemberContract(access, name, descriptor));
            skipAttributes(input);
        }
        return List.copyOf(members);
    }

    private static void skipAttributes(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            input.readUnsignedShort();
            input.skipNBytes(Integer.toUnsignedLong(input.readInt()));
        }
    }

    private record ClassContract(int access, List<MemberContract> fields, List<MemberContract> methods) {}

    private record MemberContract(int access, String name, String descriptor) {}
}
