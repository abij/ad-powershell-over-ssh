package nl.abij.service;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vngx.jsch.exception.JSchException;

public class PowershellServiceIntegrationTest {

    private PowershellService powershellService;

    @BeforeClass
    public static void runOnce() throws JSchException {
        PowershellSession session = PowershellSession.getInstance();
        session.setHost("host"); // SSH-server on AD
        session.setUsername("ad-username");
        session.setPassword("password");
        session.init();
    }

    @Before
    public void setUp() throws Exception {
        powershellService = new PowershellService();
        powershellService.setAdUserPath("OU=Users,DC=local"); // where are the user created in AD.
    }

    @Test
    public void createUser() throws Exception {
        powershellService.createUser("Alexander Bij", "fake@email.com", "abij01", "Welcome01");
    }

    @Test
    public void removeUser() throws Exception {
        powershellService.removeUser("abij01");
    }
}
