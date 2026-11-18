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

    /** A document that exists as a file, so a student can download it. */
    public record DocumentFile(String name, String title) {
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

    /** The application forms, which are kept in their own folder so they can be handed over together. */
    List<DocumentFile> forms() {
        return jdbc.query("select source_name, title from rag_documents where source_name like ? order by title",
                (row, index) -> new DocumentFile(row.getString("source_name"), row.getString("title")),
                DocumentIngestionService.FORMS_FOLDER + "/%");
    }

    /**
     * The closest pieces, each followed by the next piece of the same document, so a list that runs
     * over a boundary (such as the required documents) arrives whole.
     */
    List<StoredChunk> nearest(double[] query, int limit) {
        return jdbc.query("""
                with hits as (
                    select document_id, position, embedding <=> ?::vector as distance
                    from rag_chunks
                    order by distance
                    limit ?
                )
                select d.title, c.content
                from rag_chunks c
                join rag_documents d on d.id = c.document_id
                join lateral (
                    select min(h.distance) as distance
                    from hits h
                    where h.document_id = c.document_id and c.position between h.position and h.position + 1
                ) near on near.distance is not null
                order by near.distance, c.document_id, c.position
                """,
                (row, index) -> new StoredChunk(row.getString("title"), row.getString("content")),
                literal(query), limit);
    }

    private static String literal(double[] vector) {
        return DoubleStream.of(vector).mapToObj(Double::toString).collect(Collectors.joining(",", "[", "]"));
    }
}
