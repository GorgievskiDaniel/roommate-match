package mk.roommatematch.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Weights of the score categories, each set summing to 1.
 *
 * @param sameFacultyBonus added to the total, as a share of 1, for two students of the same faculty
 */
@ConfigurationProperties("app.matching")
public record MatchingProperties(ApartmentWeights apartment, DormWeights dorm, double sameFacultyBonus) {

    public record ApartmentWeights(double daily, double together, double rent, double hobbies, double age) {
    }

    public record DormWeights(double daily, double together, double hobbies, double age) {
    }
}
