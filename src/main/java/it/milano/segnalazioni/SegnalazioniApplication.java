package it.milano.segnalazioni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SegnalazioniApplication {
    public static void main(String[] args) {
        SpringApplication.run(SegnalazioniApplication.class, args);
    }
}
