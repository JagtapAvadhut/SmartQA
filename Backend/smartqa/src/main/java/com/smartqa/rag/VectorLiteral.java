package com.smartqa.rag;

/**
 * Converts float embeddings to/from pgvector text literals for R2DBC binding.
 * Format: [0.1,0.2,...] — cast with ::vector in SQL.
 */
public final class VectorLiteral {

    private VectorLiteral() {
    }

    public static String toLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding must be non-empty");
        }
        StringBuilder sb = new StringBuilder(embedding.length * 8);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Float.toString(embedding[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    public static float[] fromLiteral(String literal) {
        if (literal == null || literal.isBlank()) {
            return new float[0];
        }
        String body = literal.trim();
        if (body.startsWith("[") && body.endsWith("]")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
