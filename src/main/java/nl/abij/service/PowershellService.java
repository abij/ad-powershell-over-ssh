package nl.abij.service;

import org.apache.commons.lang.StringUtils;
import org.vngx.jsch.exception.JSchException;

import java.io.IOException;
import java.util.*;

public class PowershellService {

    private PowershellSession powershellSession;
    private String adUserPath;

    private static final String CREATE_USER =
            "New-ADUser -SamAccountName \"${samAccountName}\"" +
            " -Name \"${name}\"" +
            " -DisplayName ${displayname}" +
            " -EmailAddress \"${email}\"" +
            " -AccountPassword (ConvertTo-SecureString -AsPlainText \"${passwordPlain}\" -Force)" +
            " -ChangePasswordAtLogin $false -PasswordNeverExpires $true -Enabled $true" +
            " -path \"${adUserPath}\"";
    private static final String DELETE_USER = "Remove-ADUser -identity \"${samAccountName}\" -Confirm:$false";


    public void createUser(String displayname, String email, String username, String password) throws JSchException, IOException {
        Map<String, String> model = new LinkedHashMap<String, String>();
        model.put("samAccountName", username);
        model.put("name", username);
        model.put("password", password);
        model.put("displayname", displayname);
        model.put("email", email);
        model.put("passwordPlain", password);
        model.put("adUserGroup", adUserPath);

        powershellSession.execute(commando(CREATE_USER, model));
    }

    public void removeUser(String username) throws JSchException, IOException {
        Map<String, String> model = new LinkedHashMap<String, String>();
        model.put("samAccountName", username);

        powershellSession.execute(commando(CREATE_USER, model));
    }

    /**
     * Fill the given String with the model.
     * Template "Hi ${foo}", with model ("foo" -> "bar") will be result in: "Hi bar".
     *
     * @throws IllegalStateException when not all replace-fields are filled.
     * @see StringUtils#replaceEach(String, String[], String[])
     */
    private String commando(String template, Map<String, String> model) {
        List<String> searchList = new ArrayList<String>(model.size());
        for (String key : model.keySet()) {
            searchList.add("${" + key + "}");
        }
        String[] replaceList = model.values().toArray(new String[model.size()]);
        String command = StringUtils.replaceEach(template, searchList.toArray(new String[model.size()]), replaceList);

        if (command.matches("$\\{.+\\}")) {
            throw new IllegalStateException("Command contains unfilled parameters: " + command);
        }
        return command;
    }

    public void setPowershellSession(PowershellSession powershellSession) {
        this.powershellSession = powershellSession;
    }

    public void setAdUserPath(String adUserPath) {
        this.adUserPath = adUserPath;
    }
}
