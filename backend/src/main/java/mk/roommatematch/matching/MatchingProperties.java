package mk.roommatematch.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.matching")
public record MatchingProperties(ApartmentWeights apartment, DormWeights dorm, double differentRoomTypeFactor) {

    public record ApartmentWeights(double habits, double rent, double location, double hobbies) {
    }

    public record DormWeights(double habits, double hobbies) {
    }
}
