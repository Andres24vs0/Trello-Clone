package com.ingenieriadesoftware.EstoNoEsTrello;

import com.ingenieriadesoftware.EstoNoEsTrello.Controllers.BlockController;
import com.ingenieriadesoftware.EstoNoEsTrello.Controllers.WorkSpaceController;
import com.ingenieriadesoftware.EstoNoEsTrello.JsonControllers.UserJsonController;
import com.ingenieriadesoftware.EstoNoEsTrello.model.Block;
import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import com.ingenieriadesoftware.EstoNoEsTrello.model.WorkSpace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

public class MemoryProfilingTest {

    @Test
    @DisplayName("ENFOQUE 2: Profiling de memoria y ciclo de vida de destrucción")
    public void testBlockDestructionMemoryProfile() throws IOException {
        // Arrange (Preparar)
        User fakeUser = new User("test@ucab.edu.ve", "pass");
        ArrayList<User> fakeUsers = new ArrayList<>();
        fakeUsers.add(fakeUser);
        
        WorkSpace fakeWorkspace = new WorkSpace(1L, "WS", "Desc", new ArrayList<>());
        fakeUser.getWorkspaces().add(fakeWorkspace);
        
        Block blockToDestroy = new Block(100L, "TempBlock", new ArrayList<>());
        fakeWorkspace.getBlocks().add(blockToDestroy);

        try (MockedStatic<UserJsonController> mockedUserJson = mockStatic(UserJsonController.class);
             MockedStatic<WorkSpaceController> mockedWorkspace = mockStatic(WorkSpaceController.class)) {
             
            // Aislando disco duro (JSONs)
            mockedUserJson.when(UserJsonController::findTotalUsers).thenReturn(fakeUsers);
            mockedUserJson.when(() -> UserJsonController.deleteUser(anyString())).thenAnswer(i -> null);
            mockedUserJson.when(() -> UserJsonController.saveUser(any(User.class))).thenAnswer(i -> null);
            
            // Forzamos el retorno del Workspace mockeado para evitar cálculos extra
            mockedWorkspace.when(() -> WorkSpaceController.findWorkSpace(eq(1L), any(User.class))).thenReturn(fakeWorkspace);
            
            // Act (Actuar)
            BlockController.deleteBlock(100L, 1L, fakeUser);
            
            // Forzar recolección de basura para validar ciclo de vida
            blockToDestroy = null;
            System.gc();
            
            // Assert (Afirmar)
            boolean containsBlock = false;
            for(Block b : fakeWorkspace.getBlocks()) {
                if(b.getId().equals(100L)) {
                    containsBlock = true;
                    break;
                }
            }
            assertFalse(containsBlock, "La memoria/referencia del bloque debe liberarse correctamente al eliminarlo");
        }
    }
}
