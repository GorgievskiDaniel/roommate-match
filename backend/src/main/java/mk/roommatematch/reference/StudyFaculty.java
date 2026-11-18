package mk.roommatematch.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A faculty or university a student can pick in the questionnaire. */
@Entity
@Table(name = "study_faculties")
public class StudyFaculty {

    @Id
    private Long id;

    private String name;

    @Column(name = "short_name")
    private String shortName;

    private String latin;

    private String university;

    private String city;

    private int position;

    protected StudyFaculty() {
    }

    public Long getId() {
        return id;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLatin() {
        return latin;
    }

    public String getUniversity() {
        return university;
    }

    public String getCity() {
        return city;
    }

    /** What is saved on the profile and shown to other students, e.g. "Факултет за … (ФИНКИ), Скопје". */
    public String label() {
        return name + (shortName == null ? "" : " (" + shortName + ")") + ", " + city;
    }
}
