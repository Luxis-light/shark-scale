package offlinewallet.keystorefile;

import offlineWallet.OfflineWallet;
import offlineWallet.keystorefile.GenerateKeystorefile;
import offlineWallet.keystorefile.KeystoreGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    @Test
    @DisplayName("Sollte eine Transaktion erfolgreich signieren")
    void shouldSignTransactionSuccessfully() throws SignatureException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Gebe eine Dummy-Transaktion vor
        ECKeyPair testAdresse = Keys.createEcKeyPair();
        String recipientAddress = Credentials.create(testAdresse).getAddress();

        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                BigInteger.valueOf(0), // Nonce
                BigInteger.valueOf(20_000_000_000L), // Gas Price
                BigInteger.valueOf(21000), // Gas Limit
                recipientAddress, // Dummy Recipient
                BigInteger.valueOf(1_000_000_000_000_000_000L) // Value (1 Ether)
        );

        // When
        String signedTxHex = offlineWallet.signTransaction(rawTransaction);

        // Then
        assertNotNull(signedTxHex, "Die signierte Transaktion sollte nicht null sein");
        assertTrue(signedTxHex.startsWith("0x"), "Die signierte Transaktion sollte mit '0x' beginnen");
        assertTrue(signedTxHex.length() > 100, "Die signierte Transaktion sollte eine signifikante Länge haben"); // Eine typische Länge

        // Überprüfung der Signatur (optional, aber empfohlen für umfassende Tests)
        SignedRawTransaction decodedTx = (SignedRawTransaction) TransactionDecoder.decode(signedTxHex);
        assertNotNull(decodedTx, "Signierte Transaktion sollte dekodierbar sein");
        assertEquals(rawTransaction.getValue(), decodedTx.getValue(), "Wert sollte übereinstimmen");
        assertEquals(rawTransaction.getTo(), decodedTx.getTo(), "Empfänger sollte übereinstimmen");
        assertEquals(rawTransaction.getGasLimit(), decodedTx.getGasLimit(), "Gas Limit sollte übereinstimmen");
        assertEquals(rawTransaction.getGasPrice(), decodedTx.getGasPrice(), "Gas Price sollte übereinstimmen");

        // Prüfen, ob die Absenderadresse aus der Signatur mit der Wallet-Adresse übereinstimmt
        String senderAddress = decodedTx.getFrom();
        assertEquals(offlineWallet.getHexadresse(), senderAddress, "Absenderadresse sollte mit der Wallet-Adresse übereinstimmen");
    }

    @Test
    @DisplayName("Sollte die Keystore-Generierung an das injizierte Interface delegieren")
    void shouldDelegateKeystoreGenerationToInjectedInterface() throws CipherException, IOException {
        // Arrange
        String expectedFilePath = tempDir.getAbsolutePath() + File.separator + "test_wallet_mock.json";
        // Simulieren, dass der Mock-Generator einen Pfad zurückgibt, wenn er aufgerufen wird
        when(mockKeystoreGenerator.generateKeystoreFile(eq(password), eq(testCredentials.getEcKeyPair()), eq(tempDir), eq(true)))
                .thenReturn(expectedFilePath);

        // When
        // Rufe die Methode auf, die den Mock verwenden sollte
        String returnedPath = offlineWallet.exportWalletToKeystoreFile(password, tempDir, null);

        // Then
        assertEquals(expectedFilePath, returnedPath, "Der zurückgegebene Pfad sollte dem vom Mock entsprechen");
        // Überprüfe, ob die Methode auf dem Mock tatsächlich aufgerufen wurde
        verify(mockKeystoreGenerator).generateKeystoreFile(password, testCredentials.getEcKeyPair(), tempDir, true);
    }



}
