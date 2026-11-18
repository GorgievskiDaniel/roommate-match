package mk.roommatematch.rag;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assistant_messages")
public class AssistantMessage {

    public enum Role { USER, ASSISTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String content;

    /** JSON arrays of document names. */
    private String sources;

    private String attachments;

    private String offeredFiles;

    /** JSON array of {title, url} for an answer found on the web. */
    private String webLinks;

    protected AssistantMessage() {
    }

    public AssistantMessage(Long userId, Role role, String content, String sources, String attachments,
                            String offeredFiles, String webLinks) {
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.sources = sources;
        this.attachments = attachments;
        this.offeredFiles = offeredFiles;
        this.webLinks = webLinks;
    }

    public String getWebLinks() {
        return webLinks;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSources() {
        return sources;
    }

    public String getAttachments() {
        return attachments;
    }

    public String getOfferedFiles() {
        return offeredFiles;
    }
}
