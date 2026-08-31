package com.phomoria.update;

public final class VersionComparator {
    private VersionComparator() {}

    public static int compare(String a, String b) {
        int[] va = parse(a);
        int[] vb = parse(b);
        int length = Math.max(va.length, vb.length);

        for (int i = 0; i < length; i++) {
            int pa = i < va.length ? va[i] : 0;
            int pb = i < vb.length ? vb[i] : 0;
            if (pa != pb) {
                return Integer.compare(pa, pb);
            }
        }
        return 0;
    }

    public static boolean isOlder(String installed, String required) {
        return compare(installed, required) < 0;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return new int[]{0};
        }

        String clean = version.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1);
        }

        String[] parts = clean.split("\\.");
        int[] result = new int[parts.length];

        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                result[i] = 0;
            }
        }

        return result;
    }
}
