package mk.roommatematch.matching;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "match_explanations")
public class MatchExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long viewerId;

    private Long otherId;

    private String inputHash;

    private String text;

    protected MatchExplanation() {
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getText() {
        return text;
    }
}
