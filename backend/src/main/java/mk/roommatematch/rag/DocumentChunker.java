package mk.roommatematch.rag;

import java.util.ArrayList;
import java.util.List;

/** Splits a document into pieces small enough to search, without cutting paragraphs when it can avoid it. */
final class DocumentChunker {

    static final int MAX_CHARS = 1200;
    /** Carried into the next piece of a long paragraph, so a sentence on the boundary stays findable. */
    static final int OVERLAP_CHARS = 150;

    private DocumentChunker() {
    }

    static List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : text.replace("\r\n", "\n").split("\n\\s*\n")) {
            String paragraph = block.trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (!current.isEmpty() && current.length() + paragraph.length() + 2 > MAX_CHARS) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (paragraph.length() > MAX_CHARS) {
                chunks.addAll(splitLong(paragraph));
            } else {
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(paragraph);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static List<String> splitLong(String paragraph) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHARS, paragraph.length());
            if (end < paragraph.length()) {
                int space = paragraph.lastIndexOf(' ', end);
                if (space > start + OVERLAP_CHARS) {
                    end = space;
                }
            }
            pieces.add(paragraph.substring(start, end).trim());
            if (end == paragraph.length()) {
                break;
            }
            start = end - OVERLAP_CHARS;
        }
        return pieces;
    }
}
