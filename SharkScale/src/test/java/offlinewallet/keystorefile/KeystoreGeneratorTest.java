package offlinewallet.keystorefile;

import offlineWallet.keystorefile.KeystoreGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.junit.jupiter.api.Assertions.*;

class KeystoreGeneratorTest {

    private final String password = "a-secure-password-123";
    @TempDir
    Path tempDir;
    private KeystoreGenerator keystoreGenerator;
    private ECKeyPair testKeyPair;

    @BeforeEach
    void setUp() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        keystoreGenerator = new KeystoreGenerator();
        testKeyPair = Keys.createEcKeyPair();
    }

    @Test
    @DisplayName("Sollte eine Keystore-Datei mit Zeitstempel-Namen generieren, wenn kein Dateiname angegeben ist")
    void generateKeystoreFile_withNullFilename_shouldGenerateTimestampedFile() throws CipherException, IOException {
        // Arrange
        File destinationDirectory = tempDir.toFile();

        // Act
        String generatedFileName = keystoreGenerator.generateKeystoreFile(password, testKeyPair, destinationDirectory, null);

        // Assert
        assertNotNull(generatedFileName);
        assertTrue(generatedFileName.startsWith("UTC--"));

        File[] filesInDir = destinationDirectory.listFiles();
        assertNotNull(filesInDir);
        assertEquals(1, filesInDir.length);
        assertEquals(generatedFileName, filesInDir[0].getName());

        assertDoesNotThrow(() -> {
            Credentials loadedCredentials = WalletUtils.loadCredentials(password, filesInDir[0]);
            // --- KORREKTUR: Füge das "0x"-Präfix zum erwarteten Wert hinzu ---
            String expectedAddress = "0x" + Keys.getAddress(testKeyPair);
            assertEquals(expectedAddress, loadedCredentials.getAddress().toLowerCase());
        }, "Die erstellte Keystore-Datei sollte mit dem korrekten Passwort entschlüsselbar sein.");
    }

    @Test
    @DisplayName("Sollte eine Keystore-Datei mit dem angegebenen Namen erstellen, wenn ein Dateiname vorhanden ist")
    void generateKeystoreFile_withCustomFilename_shouldCreateFileWithGivenName() throws CipherException, IOException {
        // Arrange
        File destinationDirectory = tempDir.toFile();
        String customFilename = "my-test-wallet.json";

        // Act
        String returnedFilePath = keystoreGenerator.generateKeystoreFile(password, testKeyPair, destinationDirectory, customFilename);

        // Assert
        assertNotNull(returnedFilePath);
        assertTrue(returnedFilePath.endsWith(customFilename));

        File expectedFile = new File(destinationDirectory, customFilename);
        assertTrue(expectedFile.exists());

        assertDoesNotThrow(() -> {
            Credentials loadedCredentials = WalletUtils.loadCredentials(password, expectedFile);
            // --- KORREKTUR: Füge das "0x"-Präfix zum erwarteten Wert hinzu ---
            String expectedAddress = "0x" + Keys.getAddress(testKeyPair);
            assertEquals(expectedAddress, loadedCredentials.getAddress().toLowerCase());
        }, "Die erstellte Keystore-Datei sollte mit dem korrekten Passwort entschlüsselbar sein.");
    }
}