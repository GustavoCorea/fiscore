package com.fiscore.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Arranca el contexto completo. Al hacerlo, Spring Data valida todas las
 * consultas declaradas en los repositorios, por lo que este test detecta
 * errores de JPQL antes de desplegar.
 */
@SpringBootTest
@ActiveProfiles("test")
class CoreApplicationTests {

	@Test
	void contextLoads() {
	}

}
