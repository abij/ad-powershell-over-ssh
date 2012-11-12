package nl.abij.service;

import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vngx.jsch.exception.JSchException;

public class PowershellServiceIntegrationTest {

    private static PowershellSession session;

    private PowershellService powershellService;

    @BeforeClass
    public static void runOnce() throws JSchException {
        session = new PowershellSession();
        session.setHost("ad-with-ssh-dns");
        session.setUsername("ad-username");
        session.setPassword("password");
        session.init();
    }

    @AfterClass
    public static void atLast() {
        session.disconnect();
    }

    @Before
    public void setUp() throws Exception {
        powershellService = new PowershellService();
        powershellService.setPowershellSession(session);
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
