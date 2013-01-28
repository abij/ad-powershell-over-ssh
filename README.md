Interaction with AD over SSH.

These Java classes are a client to execute ActiveDirectory commands via the Powershell over SSH.
- SSH client is Jsch
- Java 5
- Maven to setup project and manage dependencies.

===== PowershellIntegrationTest, interact with your AD ========
Requirements:
- AD domain controller with SSH-Server like Bit-wise.
- Access via SSH-Server to AD controller with username / password.
- Correct rights to create / delete user in specific group (OU)

Just configure your server, usergroup-location and username / password.
