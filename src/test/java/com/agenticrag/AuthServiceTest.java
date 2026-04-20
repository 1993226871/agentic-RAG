package com.agenticrag;

import com.agenticrag.service.AuthService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @Test
    void shouldSupportDefaultUserAndRegister() {
        AuthService authService = new AuthService();
        Assertions.assertTrue(authService.login("root", "123456"));
        Assertions.assertFalse(authService.login("root", "bad"));
        Assertions.assertTrue(authService.register("u1", "p1"));
        Assertions.assertTrue(authService.login("u1", "p1"));
    }
}
