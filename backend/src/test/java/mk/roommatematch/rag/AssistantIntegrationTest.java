package mk.roommatematch.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mk.roommatematch.ai.AiUnavailableException;
import mk.roommatematch.ai.GeminiClient;
import mk.roommatematch.support.RecordingMailer;
import mk.roommatematch.support.TestAccounts;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(properties = "app.rag.documents-dir=target/test-docs")
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
    RecordingMailer mail;

    @Autowired
    ObjectMapper json;

    @Autowired
    RagStore store;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    mk.roommatematch.reference.DormRepository dorms;

    /** The same folder the application serves downloads from. */
    private final Path folder = Path.of("target/test-docs");

    private DocumentIngestionService ingestion;

    /** A stand-in for meaning: texts about the same topic get the same direction. */
    private static double[] vectorFor(String text) {
        double[] vector = new double[DIMENSIONS];
        vector[text.contains("конкурс") ? 0 : text.contains("правила") ? 1 : 2] = 1;
        return vector;
    }

    @BeforeEach
    void setUp() throws IOException {
        if (Files.isDirectory(folder)) {
            try (var files = Files.walk(folder)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    if (!file.equals(folder)) {
                        Files.delete(file);
                    }
                }
            }
        }
        Files.createDirectories(folder);
        jdbc.update("delete from rag_documents");
        ingestion = new DocumentIngestionService(new RagProperties(folder.toString(), true, 2, true), store, gemini, dorms);
        when(gemini.embed(anyList())).thenAnswer(call ->
                call.<List<String>>getArgument(0).stream().map(AssistantIntegrationTest::vectorFor).toList());
    }

    private void write(String name, String text) throws IOException {
        Files.createDirectories(folder.resolve(name).getParent());
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
    void theNextPieceOfAFoundPieceComesAlong() throws Exception {
        write("konkurs.txt", "Потребни документи за конкурс: " + "а".repeat(1150)
                + "\n\n" + "Последниот документ на списокот е изјавата од родител.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("{\"found\":false}"));

        ask("Кои документи за конкурс ми требаат?");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(anyString(), prompt.capture(), any());
        assertThat(prompt.getValue()).contains("изјавата од родител");
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
        assertThat(prompt.getValue()).contains("Sources:\n[1] konkurs").endsWith("Question: Кога е конкурс?");
    }

    @Test
    void historyIsKeptPerStudentAndCanBeCleared() throws Exception {
        write("konkurs.txt", "Рокот за конкурс е до 20 август.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":true,"answer":"До 20 август.","sources":[1],"offerFiles":[],"acceptsOffer":false}"""));
        String ana = token();
        String vera = token();

        send(ana, "Кога е конкурсот?").andExpect(status().isOk());

        history(ana)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("Кога е конкурсот?"))
                .andExpect(jsonPath("$[1].content").value("До 20 август."))
                .andExpect(jsonPath("$[1].sources[0]").value("konkurs"));
        history(vera).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/assistant/history")
                .header("Authorization", "Bearer " + ana)).andExpect(status().isNoContent());
        history(ana).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void followUpSeesEarlierTurnsAndSearchesWithThePreviousQuestion() throws Exception {
        write("konkurs.txt", "Рокот за конкурс е до 20 август.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":true,"answer":"До 20 август.","sources":[1],"offerFiles":[],"acceptsOffer":false}"""));
        String token = token();
        send(token, "Кога е конкурсот?");
        clearInvocations(gemini);
        when(gemini.embed(anyList())).thenAnswer(call ->
                call.<List<String>>getArgument(0).stream().map(AssistantIntegrationTest::vectorFor).toList());

        send(token, "А за втор круг?");

        verify(gemini).embed(List.of(
                "task: search result | query: А за втор круг?",
                "task: search result | query: Кога е конкурсот? А за втор круг?"));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(anyString(), prompt.capture(), any());
        assertThat(prompt.getValue())
                .contains("Student: Кога е конкурсот?\nAssistant: До 20 август.")
                .endsWith("Question: А за втор круг?");
    }

    @Test
    void requiredDocumentsAreOfferedAndAllFormsAreHandedOverWhenTheStudentAgrees() throws Exception {
        write("konkurs.txt", "За конкурс се потребни образец и изјава.");
        write("obrasci/obrazec.txt", "Образец за пријава.");
        write("obrasci/izjava.txt", "Изјава.");
        ingestion.ingest();
        String token = token();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":true,"answer":"Потребни се образец и изјава.","sources":[1],"offerForms":true,"acceptsOffer":false}"""));

        send(token, "Кои документи ми се потребни?")
                .andExpect(jsonPath("$.answer").value("Потребни се образец и изјава.\n\n" + AssistantService.OFFER_QUESTION))
                .andExpect(jsonPath("$.attachments").isEmpty());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(gemini.generateJson(anyString(), prompt.capture(), any())).thenReturn(json.readTree("""
                {"found":false,"answer":"","sources":[],"offerFiles":[],"acceptsOffer":true}"""));

        send(token, "Да")
                .andExpect(jsonPath("$.answer").value(AssistantService.HANDOVER_ANSWER))
                .andExpect(jsonPath("$.attachments.length()").value(2))
                .andExpect(jsonPath("$.attachments[1].name").value("obrasci/obrazec.txt"))
                .andExpect(jsonPath("$.attachments[1].title").value("obrazec"));
        assertThat(prompt.getValue()).contains("Pending offer: the assistant offered to attach obrasci/izjava.txt, obrasci/obrazec.txt");

        mvc.perform(get("/api/assistant/files?name=obrasci/obrazec.txt").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Образец за пријава."));
        mvc.perform(get("/api/assistant/files?name=konkurs.txt").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        history(token).andExpect(jsonPath("$[3].attachments.length()").value(2));
    }

    @Test
    void anOfferIsOnlyGoodForTheNextMessage() throws Exception {
        write("konkurs.txt", "За конкурс се потребни образец и изјава.");
        write("obrasci/obrazec.txt", "Образец за пријава.");
        ingestion.ingest();
        String token = token();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":true,"answer":"Потребни се образец и изјава.","sources":[1],"offerForms":true,"acceptsOffer":false}"""));
        send(token, "Кои документи ми се потребни?");
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":true,"answer":"До август.","sources":[1],"offerFiles":[],"acceptsOffer":false}"""));
        send(token, "Кога е рокот?");
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":false,"answer":"","sources":[],"offerFiles":[],"acceptsOffer":true}"""));

        send(token, "Да").andExpect(jsonPath("$.attachments").isEmpty());
    }

    @Test
    void onlyIndexedFilesFromTheFolderCanBeDownloaded() throws Exception {
        write("konkurs.txt", "Текст.");
        ingestion.ingest();
        String token = token();

        mvc.perform(get("/api/assistant/files?name=nepostoi.pdf").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/assistant/files?name=../../pom.xml").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/assistant/files?name=__dorm_facts__").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/assistant/files?name=konkurs.txt")).andExpect(status().isUnauthorized());
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
    void whatTheDocumentsLackIsLookedUpOnTheWebWithItsLinks() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":false,"answer":"","sources":[]}"""));
        when(gemini.searchWeb(anyString(), anyString())).thenReturn(Optional.of(new GeminiClient.SearchAnswer(
                "Домот Пелагонија моментално се реновира.",
                List.of(new GeminiClient.Citation("mon.gov.mk", "https://mon.gov.mk/vest")),
                "<div>предлози</div>")));
        String token = token();

        send(token, "Дали работи домот Пелагонија?")
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.answer").value("Домот Пелагонија моментално се реновира."))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.webLinks[0].url").value("https://mon.gov.mk/vest"))
                .andExpect(jsonPath("$.searchSuggestions").value("<div>предлози</div>"));

        history(token)
                .andExpect(jsonPath("$[1].webLinks[0].title").value("mon.gov.mk"));
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(gemini).searchWeb(anyString(), input.capture());
        assertThat(input.getValue()).endsWith("Question: Дали работи домот Пелагонија?");
    }

    @Test
    void aWebAnswerWithoutSourcesOrWithoutAnAnswerIsNotShown() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":false,"answer":"","sources":[]}"""));
        when(gemini.searchWeb(anyString(), anyString())).thenReturn(
                Optional.of(new GeminiClient.SearchAnswer("Непроверено.", List.of(), "")),
                Optional.of(new GeminiClient.SearchAnswer("НЕМА",
                        List.of(new GeminiClient.Citation("a", "https://a.mk")), "")));

        ask("Колку чини пицата?").andExpect(jsonPath("$.answer").value(AssistantService.NOT_FOUND_ANSWER));
        ask("Колку чини пицата?")
                .andExpect(jsonPath("$.answer").value(AssistantService.NOT_FOUND_ANSWER))
                .andExpect(jsonPath("$.webLinks").isEmpty());
    }

    @Test
    void unavailableWebSearchFallsBackToTheUsualAnswer() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":false,"answer":"","sources":[]}"""));
        when(gemini.searchWeb(anyString(), anyString())).thenThrow(new AiUnavailableException("no quota"));

        ask("Дали работи домот Пелагонија?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.answer").value(AssistantService.NOT_FOUND_ANSWER));
    }

    @Test
    void anAnswerFromTheDocumentsNeverSearchesTheWeb() throws Exception {
        write("konkurs.txt", "Рокот за конкурс.");
        ingestion.ingest();
        clearInvocations(gemini);
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"found":true,"answer":"До август.","sources":[1]}"""));

        ask("Кога е конкурсот?").andExpect(jsonPath("$.webLinks").isEmpty());

        verify(gemini, never()).searchWeb(anyString(), anyString());
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

    private ResultActions history(String token) throws Exception {
        return mvc.perform(get("/api/assistant/history").header("Authorization", "Bearer " + token));
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
        return TestAccounts.register(mvc, mail);
    }
}
