package offlinewallet.keystorefile;

import offlineWallet.OfflineWallet;
import offlineWallet.keystorefile.GenerateKeystorefile;
import offlineWallet.keystorefile.KeystoreGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GenerateKeystorefileTest {
    private GenerateKeystorefile mockKeystoreGenerator;
    private Credentials testCredentials;
    private OfflineWallet offlineWallet;
    private File tempDir;
    private final String password = "testPassword456!";

    @BeforeEach
    void setUp() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, IOException {
        // Erstelle Mock für das Interface
        mockKeystoreGenerator = mock(KeystoreGenerator.class); // Mock die konkrete Implementierung

        // Erstelle Test-Credentials
        testCredentials = Credentials.create(Keys.createEcKeyPair());
        offlineWallet = new OfflineWallet(testCredentials, mockKeystoreGenerator);

        // Erstelle temporäres Verzeichnis für Dateitests
        tempDir = Files.createTempDirectory("offline_wallet_test_").toFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Loscht das temprare Verzeichniss
        if (tempDir.exists()) {
            Files.walk(tempDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    @DisplayName("Sollte eine neue Wallet erfolgreich generieren")
    void shouldGenerateNewWalletSuccessfully() {

        assertDoesNotThrow(() -> {
            OfflineWallet offlineWallet = OfflineWallet.generateNewWallet();
            assertNotNull(offlineWallet);
            assertNotNull(offlineWallet.getHexadresse());
            assertNotNull(offlineWallet.getPublicKey());
            assertTrue(offlineWallet.getHexadresse().startsWith("0x"));
            assertTrue(offlineWallet.getPublicKey().length() > 0);
            System.out.println();
        });
    }

    @Test
    @DisplayName("Sollte die korrekte Hex-Adresse zurückgeben")
    void shouldReturnCorrectHexAddress() {
        assertEquals(testCredentials.getAddress(), offlineWallet.getHexadresse());
    }

    @Test
    @DisplayName("Sollte den korrekten öffentlichen Schlüssel zurückgeben")
    void shouldReturnCorrectPublicKey() {
        assertEquals(testCredentials.getEcKeyPair().getPublicKey().toString(16), offlineWallet.getPublicKey());
    }

    @Test
    @DisplayName("Sollte zwei verschiedene Wallets generieren")
    void shouldGenerateTwoDifferentWallets() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        OfflineWallet wallet1 = OfflineWallet.generateNewWallet();
        OfflineWallet wallet2 = OfflineWallet.generateNewWallet();

        assertNotEquals(wallet1.getHexadresse(), wallet2.getHexadresse(), "Zwei generierte Wallets sollten unterschiedliche Adressen haben.");
        assertNotEquals(wallet1.getPublicKey(), wallet2.getPublicKey(), "Zwei generierte Wallets sollten unterschiedliche öffentliche Schlüssel haben.");
    }


}
