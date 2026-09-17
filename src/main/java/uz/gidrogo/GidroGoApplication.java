package uz.gidrogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = "uz.gidrogo.modules")
public class GidroGoApplication {
    public static void main(String[] args) {
        SpringApplication.run(GidroGoApplication.class, args);
    }
}
