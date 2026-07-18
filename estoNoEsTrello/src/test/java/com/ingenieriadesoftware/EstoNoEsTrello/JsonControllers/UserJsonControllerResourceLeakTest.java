package com.ingenieriadesoftware.EstoNoEsTrello.JsonControllers;

import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * CP-U-02 — UserJsonController.saveUser(User)
 * Plan de Pruebas ST-KANBAN-PERF-PLAN, sección 5.1 / 6.1.
 * <p>
 * Entrada: usuario válido; escritura forzada a fallar mediante un mock
 * de FileWriter que lanza IOException.
 * Resultado esperado (según el plan): "El recurso de archivo se libera
 * correctamente incluso ante excepción. (Nota: el método actual NO usa
 * try-with-resources; SE ESPERA QUE ESTE CASO FALLE hasta que se corrija)".
 * <p>
 * TÉCNICA: en vez de refactorizar UserJsonController para inyectar el
 * FileWriter (opción que el plan también permite), se usa
 * Mockito.mockConstruction (disponible sin dependencias adicionales desde
 * Mockito 5, ya incluido transitivamente en spring-boot-starter-test) para
 * interceptar CUALQUIER "new FileWriter(...)" durante la ejecución del
 * método y forzar que su write() lance IOException.
 * <p>
 * ESTADO ESPERADO DE ESTA PRUEBA: EN ROJO (falla) mientras
 * UserJsonController.saveUser() no use try-with-resources. Este es el
 * comportamiento documentado y deseado por el plan de pruebas: la prueba
 * existe para dejar constancia reproducible del defecto, no para "pasar"
 * artificialmente. Cuando el equipo de desarrollo corrija el método
 * (envolviendo el FileWriter en un try-with-resources), esta prueba
 * pasará a verde sin modificar su código.
 */
class UserJsonControllerResourceLeakTest {

    @Test
    @DisplayName("CP-U-02: saveUser() debe cerrar el FileWriter incluso si falla la escritura "
            + "(FALLA ESPERADA en el estado actual del código, ver nota del plan)")
    void saveUser_shouldCloseFileWriter_evenWhenWriteFails() throws IOException {
        User user = new User("recurso@test.com", "Password123!");

        try (MockedConstruction<FileWriter> mockedFileWriter = Mockito.mockConstruction(
                FileWriter.class,
                (mock, context) -> doThrow(new IOException("Fallo de escritura simulado (disco lleno)"))
                        .when(mock).write(anyString()))) {

            // saveUser() envuelve cualquier IOException en RuntimeException
            assertThrows(RuntimeException.class, () -> UserJsonController.saveUser(user),
                    "saveUser() debería propagar la IOException envuelta en RuntimeException");

            FileWriter constructedWriter = mockedFileWriter.constructed().get(0);

            // Verificación central del caso de prueba: el recurso debe cerrarse
            // siempre, incluso cuando write() lanza una excepción.
            // Hoy esto FALLA porque fw.close() está después de fw.write(...)
            // sin bloque finally / try-with-resources: si write() lanza,
            // close() nunca se invoca y el descriptor de archivo queda abierto.
            verify(constructedWriter, times(1)).close();
        }
    }
}
