package br.com.vinhariaagnello.wineguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/** Ponto de entrada que configura e inicializa a aplicação Spring Boot. */
public class WineguardBackendApplication {

	/** Inicia o servidor HTTP, os componentes MVC e as integrações configuradas. */
	public static void main(String[] args) {
		SpringApplication.run(WineguardBackendApplication.class, args);
	}

}
