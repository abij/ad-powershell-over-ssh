package nl.abij.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vngx.jsch.ChannelShell;
import org.vngx.jsch.ChannelType;
import org.vngx.jsch.JSch;
import org.vngx.jsch.Session;
import org.vngx.jsch.Util;
import org.vngx.jsch.config.SSHConfigConstants;
import org.vngx.jsch.config.SessionConfig;
import org.vngx.jsch.exception.JSchException;

public class PowershellSession {

    private static final Logger LOG = LoggerFactory.getLogger(PowershellSession.class);
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int AD_MODULE_LOADINGTIME = 2000; // 2sec wait for loading...

    // required
    private String username;
    private byte[] password;
    private String host;
    // optional - defaults:
    private int port = 22;
    private String knownHostFile = null; // No knownHostFile -> don't check server fingerprint.
    private int socketTimeout = 5 * 1000;// 5s.
    private int readTimeout = 3 * 1000;  // 3s max time wait & read from server
    private int pollTimeout = 20;        // 20 ms before read again.

    private Session session;
    private ChannelShell shell;
    private InputStream fromServer;
    private OutputStream toServer;

    public void init() throws JSchException {
        JSch jSch = JSch.getInstance();

        SessionConfig config = new SessionConfig();
        config.setProperty(SSHConfigConstants.PREFFERED_AUTHS, "password");
        if (knownHostFile == null) {
            config.setProperty(SSHConfigConstants.STRICT_HOST_KEY_CHECKING, "no");
        }
        session = jSch.createSession(username, host, port, config);
    }

    public void disconnect() {
        if (shell != null && shell.isConnected()) {
            shell.disconnect();
            LOG.debug("Channel Shell is disconnected.");
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
            LOG.debug("Session is disconnected.");
        }
    }

    /* This implementation allows 1 command to be executed at the time over 1 channel.
     * With Jsch you can open multiple channels and execute commands in parallel.
     * Nothing will stop you to modify this example to your needs.*/
    public synchronized void execute(String command) throws JSchException, IOException {
        checkConnection();
        writeToServer(command);
        verifyCommandSucceded();
    }

   /* *************************************************************************************
    * Helper methodes
    * *************************************************************************************/

    private void checkConnection() throws JSchException, IOException {
        if (!session.isConnected()) {
            session.connect(socketTimeout, password);
            LOG.debug("Session connected to host: {}", session.getHost());
        } else {
            LOG.debug("Session is still connected.");
        }
        if (shell == null || !shell.isConnected()) {
            shell = session.openChannel(ChannelType.SHELL);
            shell.connect(socketTimeout);
            LOG.debug("ChannelShell is connected.");

            fromServer = shell.getInputStream();
            toServer = shell.getOutputStream();

            readFromServer(); // Read initial data: Windows PowerShell ... All rights reserved.
            loadModuleActiveDirectory();
        } else {
            LOG.debug("Channel (shell) still open.");
        }
    }

    private void loadModuleActiveDirectory() throws IOException {
        LOG.debug("import-module ActiveDirectory...");
        writeToServer("import-module ActiveDirectory");
        sleep(AD_MODULE_LOADINGTIME, "Failed to sleep after loading module ActiveDirectory.");
        verifyCommandSucceded();
    }

    private void writeToServer(String command) throws IOException {
        String commandWithEnter = command;
        if (!command.endsWith("\r\n")) {
            commandWithEnter += "\r\n";
        }
        toServer.write((commandWithEnter).getBytes(UTF8));
        toServer.flush();
    }

    private String readFromServer() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

        String linePrompt = "\\" + username + ">"; // indicates console has new-line is ready for input, stop reading.
        long timeout = System.currentTimeMillis() + readTimeout;

        while (System.currentTimeMillis() < timeout && !Util.byte2str(bos.toByteArray()).contains(linePrompt)) {
            while (fromServer.available() > 0) {
                int count = fromServer.read(buffer, 0, DEFAULT_BUFFER_SIZE);
                if (count >= 0) {
                    bos.write(buffer, 0, count);
                } else {
                    break;
                }
            }
            if (shell.isClosed()) {
                break;
            }
            // Don't spin like crazy though the while loop
            sleep(pollTimeout, "Failed to sleep between reads with pollTimeout: " + pollTimeout);
        }
        String result = bos.toString("UTF-8");
        LOG.debug("read from server:\n{}", result);
        return result;
    }

    /**
     * @throws PowershellException If the message was not executed successfully, with details info.
     */
    private void verifyCommandSucceded() throws IOException {
        String message = readFromServer();
        writeToServer("$?"); // Aks powershell status of last command?

        if (!readFromServer().contains("True")) {
            throw new PowershellException(message);
        }
    }

    private void sleep(long timeout, String msg) {
        try {
            Thread.sleep(timeout);
        } catch (Exception ee) {
            LOG.debug(msg);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        disconnect(); // Always disconnect!
    }

    /* *************************************************************************************
     * Getters & Setters
     * *************************************************************************************/
    public void setUsername(String username) {this.username = username;}
    public void setPassword(String password) {this.password = password.getBytes(UTF8);}
    public void setHost(String host) {this.host = host;}
    public void setPort(int port) {this.port = port;}
    public void setKnownHostFile(String knownHostFile) {this.knownHostFile = knownHostFile;}
    public void setSocketTimeout(int socketTimeout) {this.socketTimeout = socketTimeout;}
    public void setSession(Session session) {this.session = session;}
    public void setReadTimeout(int readTimeout) {this.readTimeout = readTimeout;}
    public void setPollTimeout(int pollTimeout) {this.pollTimeout = pollTimeout;}
}
