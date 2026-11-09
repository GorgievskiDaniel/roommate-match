package mk.roommatematch.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Plain SQL, because the vector type and its distance operator are specific to pgvector. */
@Repository
public class RagStore {

    record StoredChunk(String title, String content) {
    }

    private final JdbcTemplate jdbc;

    public RagStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Map<String, String> documentHashes() {
        Map<String, String> hashes = new HashMap<>();
        jdbc.query("select source_name, content_hash from rag_documents",
                row -> {
                    hashes.put(row.getString("source_name"), row.getString("content_hash"));
                });
        return hashes;
    }

    /** All pieces of a document change together, so a search never sees half of an update. */
    @Transactional
    public void replace(String sourceName, String title, String hash, List<String> chunks, List<double[]> embeddings) {
        delete(sourceName);
        Long documentId = jdbc.queryForObject(
                "insert into rag_documents (source_name, title, content_hash) values (?, ?, ?) returning id",
                Long.class, sourceName, title, hash);
        for (int i = 0; i < chunks.size(); i++) {
            jdbc.update("insert into rag_chunks (document_id, position, content, embedding) values (?, ?, ?, ?::vector)",
                    documentId, i, chunks.get(i), literal(embeddings.get(i)));
        }
    }

    public void delete(String sourceName) {
        jdbc.update("delete from rag_documents where source_name = ?", sourceName);
    }

    List<StoredChunk> nearest(double[] query, int limit) {
        return jdbc.query("""
                select d.title, c.content
                from rag_chunks c
                join rag_documents d on d.id = c.document_id
                order by c.embedding <=> ?::vector
                limit ?
                """,
                (row, index) -> new StoredChunk(row.getString("title"), row.getString("content")),
                literal(query), limit);
    }

    private static String literal(double[] vector) {
        return DoubleStream.of(vector).mapToObj(Double::toString).collect(Collectors.joining(",", "[", "]"));
    }
}
