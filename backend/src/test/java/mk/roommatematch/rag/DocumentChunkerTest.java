package mk.roommatematch.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    @Test
    void shortParagraphsStayTogether() {
        assertThat(DocumentChunker.chunk("Прв пасус.\n\nВтор пасус.")).containsExactly("Прв пасус.\n\nВтор пасус.");
    }

    @Test
    void windowsLineEndingsSeparateParagraphs() {
        assertThat(DocumentChunker.chunk("Прв.\r\n\r\nВтор.")).containsExactly("Прв.\n\nВтор.");
    }

    @Test
    void paragraphsAreNotCutWhenTheyFitWhole() {
        String first = "а".repeat(800);
        String second = "б".repeat(800);

        assertThat(DocumentChunker.chunk(first + "\n\n" + second)).containsExactly(first, second);
    }

    @Test
    void longParagraphIsSplitWithOverlapAndNothingIsLost() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 700; i++) {
            text.append("збор").append(i).append(' ');
        }

        List<String> chunks = DocumentChunker.chunk(text.toString());

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(DocumentChunker.MAX_CHARS));
        for (int i = 0; i < 700; i++) {
            String word = "збор" + i + " ";
            assertThat(String.join(" ", chunks) + " ").contains(word);
        }
        String lastWordsOfFirst = chunks.get(0).substring(chunks.get(0).length() - 40);
        assertThat(chunks.get(1)).contains(lastWordsOfFirst);
    }

    @Test
    void blankTextHasNoChunks() {
        assertThat(DocumentChunker.chunk("  \n\n  ")).isEmpty();
    }
}
