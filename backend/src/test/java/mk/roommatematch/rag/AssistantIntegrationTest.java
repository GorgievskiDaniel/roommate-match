package mk.roommatematch.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mk.roommatematch.ai.AiUnavailableException;
import mk.roommatematch.ai.GeminiClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class AssistantIntegrationTest {

    private static final int DIMENSIONS = 768;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @MockitoBean
    GeminiClient gemini;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    RagStore store;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    mk.roommatematch.reference.DormRepository dorms;

    @TempDir
    Path folder;

    private DocumentIngestionService ingestion;

    /** A stand-in for meaning: texts about the same topic get the same direction. */
    private static double[] vectorFor(String text) {
        double[] vector = new double[DIMENSIONS];
        vector[text.contains("конкурс") ? 0 : text.contains("правила") ? 1 : 2] = 1;
        return vector;
    }

    @BeforeEach
    void setUp() {
        jdbc.update("delete from rag_documents");
        ingestion = new DocumentIngestionService(new RagProperties(folder.toString(), true, 2), store, gemini, dorms);
        when(gemini.embed(anyList())).thenAnswer(call ->
                call.<List<String>>getArgument(0).stream().map(AssistantIntegrationTest::vectorFor).toList());
    }

    private void write(String name, String text) throws IOException {
        Files.writeString(folder.resolve(name), text);
    }

    private long documents() {
        return jdbc.queryForObject("select count(*) from rag_documents", Long.class);
    }

    @Test
    void ingestionIndexesDormFactsAndSupportedFilesOnly() throws IOException {
        write("konkurs_2026.txt", "Рокот за конкурс е до август.");
        write("slika.png", "не е документ");

        ingestion.ingest();

        assertThat(documents()).isEqualTo(2);
        assertThat(jdbc.queryForList("select title from rag_documents", String.class))
                .containsExactlyInAnyOrder("Основни податоци за домовите", "konkurs 2026");
        assertThat(jdbc.queryForObject("""
                select content from rag_chunks c join rag_documents d on d.id = c.document_id
                where d.source_name = '__dorm_facts__'""", String.class))
                .contains("ДСД Гоце Делчев е студентски дом во Скопје")
                .contains("ДСД Кочо Рацин е студентски дом во Битола")
                .contains("Со кујна (блокови А и Б)");
    }

    @Test
    void textIsReadFromPdfFiles() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Konkurs deadline is in August");
                content.endText();
            }
            document.save(folder.resolve("rok.pdf").toFile());
        }

        ingestion.ingest();

        assertThat(jdbc.queryForObject("""
                select c.content from rag_chunks c join rag_documents d on d.id = c.document_id
                where d.source_name = 'rok.pdf'""", String.class))
                .contains("Konkurs deadline is in August");
    }

    @Test
    void unchangedDocumentsAreNotEmbeddedAgain() throws IOException {
        write("konkurs.txt", "Рокот за конкурс е до август.");
        ingestion.ingest();
        clearInvocations(gemini);

        ingestion.ingest();

        verify(gemini, never()).embed(anyList());
    }

    @Test
    void changedDocumentReplacesItsOldPieces() throws IOException {
        write("konkurs.txt", "Стар текст за конкурс.");
        ingestion.ingest();

        write("konkurs.txt", "Нов текст за конкурс.");
        ingestion.ingest();

        assertThat(jdbc.queryForList("""
                select c.content from rag_chunks c join rag_documents d on d.id = c.document_id
                where d.source_name = 'konkurs.txt'""", String.class))
                .containsExactly("Нов текст за конкурс.");
    }

    @Test
    void deletedFileLeavesTheIndex() throws IOException {
        write("konkurs.txt", "Текст за конкурс.");
        ingestion.ingest();

        Files.delete(folder.resolve("konkurs.txt"));
        ingestion.ingest();

        assertThat(documents()).isEqualTo(1);
    }

    @Test
    void answerUsesTheClosestPiecesAndCitesTheirTitles() throws Exception {
        write("konkurs.txt", "Рокот за конкурс е до 20 август.");
        write("pravila.txt", "Куќните правила забрануваат бучава.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":true,"answer":"Рокот е до 20 август.","sources":[1]}"""));

        ask("Кога е конкурс?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.answer").value("Рокот е до 20 август."))
                .andExpect(jsonPath("$.sources[0]").value("konkurs"));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(anyString(), prompt.capture(), any());
        assertThat(prompt.getValue()).startsWith("Sources:\n[1] konkurs").contains("Question: Кога е конкурс?");
    }

    @Test
    void questionIsEmbeddedAsASearchQuery() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        clearInvocations(gemini);
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("{\"found\":false}"));

        ask("Кога е конкурс?").andExpect(status().isOk());

        verify(gemini).embed(List.of("task: search result | query: Кога е конкурс?"));
    }

    @Test
    void whenTheDocumentsDoNotCoverItTheAssistantSaysSo() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":false,"answer":"","sources":[]}"""));

        ask("Колку чини пицата?")
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.answer").value(AssistantService.NOT_FOUND_ANSWER))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void anAnswerMarkedNotFoundIsNeverShownEvenIfTheModelWroteOne() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":false,"answer":"Измислен одговор","sources":[1]}"""));

        ask("Прашање").andExpect(jsonPath("$.answer").value(AssistantService.NOT_FOUND_ANSWER));
    }

    @Test
    void sourceNumbersThatDoNotExistAreDropped() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":true,"answer":"Да.","sources":[99,0]}"""));

        ask("Прашање").andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void emptyIndexAnswersWithoutCallingTheModel() throws Exception {
        clearInvocations(gemini);

        ask("Кога е конкурсот?").andExpect(jsonPath("$.found").value(false));

        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    void modelFailureIsReported() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenThrow(new AiUnavailableException("down"));

        ask("Прашање").andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidQuestionsAreRejected() throws Exception {
        String token = token();

        send(token, "   ").andExpect(status().isBadRequest());
        send(token, "а".repeat(501)).andExpect(status().isBadRequest());
    }

    @Test
    void assistantRequiresLogin() throws Exception {
        mvc.perform(post("/api/assistant").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"да\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions ask(String question) throws Exception {
        return send(token(), question);
    }

    private ResultActions send(String token, String question) throws Exception {
        return mvc.perform(post("/api/assistant").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("question", question))));
    }

    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", UUID.randomUUID() + "@students.test", "password", "lozinka123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }
}
