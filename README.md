# spw-morvanium-fix

Plugin para Salt Player for Windows (SPW Workshop API `0.1.0-dev22`) para ressincronizar o estado de reprodução e a duração no Discord Rich Presence após transições contínuas do Morvanium.

## Como compilar o `.spmod`

1. Certifique-se de ter o **JDK 25** e o **Gradle** instalados no sistema (`java -version`).
2. Abra o PowerShell dentro desta pasta (`spw-morvanium-fix`) e execute:
   ```powershell
   gradle build
   ```
3. O arquivo final será gerado em:
   `build/libs/spw-morvanium-fix-1.0.0.spmod`
4. No Salt Player, vá em **Configurações > Oficina (Workshop) > Importar Mod local** e selecione o arquivo `.spmod`.
