package mk.roommatematch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RoommateMatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoommateMatchApplication.class, args);
    }
}
