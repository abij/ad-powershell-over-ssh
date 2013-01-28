package nl.abij.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * Powershell session as a Singleton. With synchronization around execute that allows this impl to perform 1 command at
 * the time over 1 channel. With Jsch you can open multiple channels and execute commands in parallel.
 *
 * Nothing will stop you to modify this example to your needs.
 */
public class PowershellSession {

    private static final Logger LOG = LoggerFactory.getLogger(PowershellSession.class);

    /* There is only 1 session, with getInstance() you can use it. */
    private PowershellSession() {}
    private static final PowershellSession INSTANCE = new PowershellSession();
    public static PowershellSession getInstance() {
        return INSTANCE;
    }

    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    // required settings
    private String username;
    private byte[] password;
    private String host;

    // optional settings with defaults:
    private int port = 22;
    private String knownHostFile = null; // No knownHostFile -> don't check server fingerprint.
    private int socketTimeout = 30 * 1000;
    private int readTimeout = 15 * 1000; // max time wait & read from server
    private int adModuleReadTimeout = 30 * 1000; // Specially for loading AD-Module.

    // Instance variables
    private Session session;
    private ChannelShell shell;
    private InputStream fromServer;
    private OutputStream toServer;
    private Pattern stopReadSign;

  /* *************************************************************************************
   * Public API: init / disconnect / execute
   * *************************************************************************************/

    /**
     * After configuring this Singleton you should call init(), to initialize a Jsch session.
     *
     * @throws JSchException
     */
    public void init() throws JSchException {
        JSch jSch = JSch.getInstance();

        SessionConfig config = new SessionConfig();
        config.setProperty(SSHConfigConstants.PREFFERED_AUTHS, "password");
        if (knownHostFile == null) {
            config.setProperty(SSHConfigConstants.STRICT_HOST_KEY_CHECKING, "no");
        }
        session = jSch.createSession(username, host, port, config);
        session.setTimeout(socketTimeout);

        // Stop reading when:  \username> [optional: controll-chars, spaces and new-lines]
        // Match case-insentitive: because your username could be different from server response.
        stopReadSign = Pattern.compile("\\\\" + username + "> ?( *(\u001B\\[\\d+[mH])\n?)*$", CASE_INSENSITIVE);
    }

    /**
     * Don't need to disconnect after execution! Only when done with singleton, called automatically when destroyed.
     */
    public void disconnect() {
        if (fromServer != null) {
            try {
                fromServer.close();
            } catch (IOException e) {
                LOG.warn("Cannot close fromServer stream.", e);
            }
        }
        if (toServer != null) {
            try {
                toServer.close();
            } catch (IOException e) {
                LOG.warn("Cannot close toServer stream.", e);
            }
        }
        if (shell != null && shell.isConnected()) {
            shell.disconnect();
            LOG.debug("Channel Shell is disconnected.");
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
            LOG.debug("Session is disconnected.");
        }
    }

    public synchronized void execute(String command) throws JSchException, IOException {
        checkConnection();
        writeToServer(command);
        verifyCommandSucceded();
    }

  /* *************************************************************************************
   * Helper methodes
   * *************************************************************************************/

    private void checkConnection() throws JSchException {
        if (session == null) {
            throw new IllegalStateException("Call init() first to initialize a Jsch session.");
        }

        if (!session.isConnected()) {
            session.connect(socketTimeout, password);
            this.password = null; // don't need the password in-memory anymore!
            LOG.debug("Session connected to host: {}:{}", session.getHost(), session.getPort());
        } else {
            LOG.debug("Session is still connected.");
        }

        try {
            if (shell == null || !shell.isConnected()) {
                shell = session.openChannel(ChannelType.SHELL);
                shell.connect(socketTimeout);
                LOG.info("ChannelShell is connected. Waiting for prompt...");

                fromServer = shell.getInputStream();
                toServer = shell.getOutputStream();

                verifyCommandSucceded(); // Read initial data: Windows PowerShell ... All rights reserved.
                loadModuleActiveDirectory();
            } else {
                LOG.debug("Channel (shell) still open.");
            }
        } catch (Exception e) {
            LOG.error("Shell is not opened correctly, failed to read prompt or load module AD, disconnect shell.");
            if (shell != null && !shell.isClosed()) {
                shell.disconnect();
            }
            throw new RuntimeException(e);
        }
    }

    private void loadModuleActiveDirectory() throws IOException {
        LOG.debug("import-module ActiveDirectory...");
        writeToServer("import-module ActiveDirectory");
        verifyCommandSucceded(adModuleReadTimeout);
        LOG.debug("module ActiveDirectory is loaded.");
    }

    private void writeToServer(String command) throws IOException {
        String commandWithEnter = command;
        if (!command.endsWith("\r\n")) {
            commandWithEnter += "\r\n";
        }
        toServer.write((commandWithEnter).getBytes(UTF8));
        toServer.flush();
    }

    private String readFromServer(long readTimeout) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

        long timeout = System.currentTimeMillis() + readTimeout;

        while (timoutNotExceedded(timeout) && !containsStopSign(bos)) {
            while (fromServer.available() > 0) {
                int count = fromServer.read(buffer, 0, DEFAULT_BUFFER_SIZE);
                if (count >= 0) {
                    bos.write(buffer, 0, count);
                } else {
                    break;
                }
            }
            if (shell.isClosed()) {
                LOG.debug("Channel closed during reading.");
                break;
            }
        }
        // Double check is actually done with command?
        if (!containsStopSign(bos)) {
            throw new PowershellException("Execution of powershell command is not finished within " + readTimeout
                    + "ms. Make sure the readTimeout is high enough.");
        }

        String result = bos.toString("UTF-8");
        LOG.trace("read from server:\n{}", result);
        return result;
    }

    private boolean timoutNotExceedded(long timeout) {
        return System.currentTimeMillis() < timeout;
    }

    private boolean containsStopSign(ByteArrayOutputStream bos) {
        String readString = Util.byte2str(bos.toByteArray());
        Matcher m = stopReadSign.matcher(readString);

        if (m.find()) {
            LOG.debug("StopSign has been read.");
            return true;
        }
        return false;
    }

    private void verifyCommandSucceded() throws IOException {
        verifyCommandSucceded(readTimeout);
    }

    /**
     * @throws PowershellException If the message was not executed successfully, with details info.
     */
    private void verifyCommandSucceded(long readTimeout) throws IOException {
        String message = readFromServer(readTimeout);
        writeToServer("$?"); // Aks powershell status of last command?

        if (!readFromServer(readTimeout).contains("True")) {
            throw new PowershellException(message);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        disconnect(); // Always disconnect!
    }

    public void setUsername(String username) {this.username = username;}
    public void setPassword(String password) {this.password = password.getBytes(UTF8);}
    public void setHost(String host) {this.host = host;}
    public void setPort(int port) {this.port = port;}
    public void setKnownHostFile(String knownHostFile) {this.knownHostFile = knownHostFile;}
    public void setSocketTimeout(int socketTimeout) {this.socketTimeout = socketTimeout;}
    public void setSession(Session session) {this.session = session;}
    public void setReadTimeout(int readTimeout) {this.readTimeout = readTimeout;}
    public void setAdModuleReadTimeout(int adModuleTimeout) {this.adModuleReadTimeout = adModuleTimeout;}
}
