package offlineTXCreatorTests;

import offlineTXCreator.OfflineTXCreator;
import offlineWallet.GetWallet;
import offlineWallet.keystorefile.GenerateKeystorefile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OfflineTXCreatorTest {

    // Instanzvariablen, damit sie in allen Testmethoden verfügbar sind
    private GenerateKeystorefile mockKeystoreGenerator;
    private Credentials testCredentials;
    private GetWallet mockWallet; // Mock des GetWallet-Interfaces
    private Web3j mockWeb3j; // Mock für Web3j
    private OfflineTXCreator offlineTXCreator;


    @BeforeEach
    public void setup() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, IOException {
        System.out.println("DEBUG: --- Setup-Methode gestartet ---");

        // Mock das GenerateKeystorefile Interface
        mockKeystoreGenerator = mock(GenerateKeystorefile.class);
        System.out.println("DEBUG: mockKeystoreGenerator erstellt. Ist es null? " + (mockKeystoreGenerator == null));

        // Erstelle echte Credentials, da die Wallet reale Schlüssel braucht
        testCredentials = Credentials.create(Keys.createEcKeyPair());
        System.out.println("DEBUG: testCredentials erstellt. Adresse: " + testCredentials.getAddress());

        // Mock das GetWallet Interface. Konfiguriere es so, dass es Test-Credentials zurückgibt
        // und die Signierfunktion korrekt simuliert.
        mockWallet = mock(GetWallet.class);
        System.out.println("DEBUG: mockWallet erstellt. Ist es null? " + (mockWallet == null));
        when(mockWallet.getCredentials()).thenReturn(testCredentials);
        // Simuliert das Signieren einer Transaktion durch die Wallet.
        // Verwendet die echte Signierlogik, aber über den Mock des Interfaces.
        when(mockWallet.signTransaction(any(RawTransaction.class))).thenAnswer(invocation -> {
            RawTransaction rawTx = invocation.getArgument(0);
            // Nutze hier die echte Signierfunktion von Web3j für die Simulation
            return org.web3j.crypto.TransactionEncoder.signMessage(rawTx, testCredentials).toString();
        });


        // Mock die Web3j Instanz für Netzwerkinteraktionen (z.B. isOnline(), fetchCurrentGasPrice())
        mockWeb3j = mock(Web3j.class);
        System.out.println("DEBUG: mockWeb3j erstellt. Ist es null? " + (mockWeb3j == null));

        // Erstelle den OfflineTXCreator mit dem gemockten Wallet und dem gemockten Web3j.
        // HINWEIS: Der Konstruktor von OfflineTXCreator muss angepasst werden,
        // um eine Web3j-Instanz zu akzeptieren: public OfflineTXCreator(GetWallet getWallet, Web3j web3j)
        System.out.println("DEBUG: Versuche OfflineTXCreator zu instanziieren mit mockWallet und mockWeb3j...");
        offlineTXCreator = new OfflineTXCreator(mockWallet, mockWeb3j);
        System.out.println("DEBUG: OfflineTXCreator erfolgreich instanziiert.");
        System.out.println("DEBUG: --- Setup-Methode beendet ---");
    }


    @Test
    @DisplayName("Sollte true zurückgeben, wenn das Netzwerk erreichbar ist (isOnline)")
    void testIsOnline_Positive() throws Exception {
        // Arrange
        // Web3j's ethBlockNumber() gibt ein Request-Objekt zurück, das dann .send() aufruft.
        // Das .send() gibt dann ein EthBlockNumber-Objekt zurück.
        // Spezifiziere den generischen Typ von Request korrekt
        Request<String, EthBlockNumber> mockRequest = mock(Request.class);
        EthBlockNumber mockedEthBlockNumberResponse = mock(EthBlockNumber.class);

        // Konfiguriere den MockWeb3j, damit er ein mockRequest zurückgibt, wenn ethBlockNumber() aufgerufen wird
        doReturn(mockRequest).when(mockWeb3j).ethBlockNumber();
        // Konfiguriere das mockRequest, damit es ein mockedEthBlockNumberResponse zurückgibt, wenn send() aufgerufen wird
        when(mockRequest.send()).thenReturn(mockedEthBlockNumberResponse);
        // Stelle sicher, dass die Antwort kein Fehler ist
        when(mockedEthBlockNumberResponse.hasError()).thenReturn(false);


        // Act
        boolean online = offlineTXCreator.isOnline();

        // Assert
        assertTrue(online,
                "isOnline() sollte true zurückgeben bei erfolgreicher Verbindung.");
        // Überprüfen, ob die Kette von Aufrufen tatsächlich stattgefunden hat
        verify(mockWeb3j).ethBlockNumber();
        verify(mockRequest).send();
    }

    @Test
    @DisplayName("Sollte false zurückgeben, wenn das Netzwerk nicht erreichbar ist (isOnline)")
    void testIsOnline_Negative() throws Exception {
        //Arrange
        Request<String, EthBlockNumber> mockRequest = mock(Request.class);

        doReturn(mockRequest).when(mockWeb3j).ethBlockNumber();

        //Simulieren dass send() einen Fehler wirft
        when(mockRequest.send()).thenThrow(new IOException("SPiele Netzwerk Fehler vor"));

        //Act
        Boolean online = offlineTXCreator.isOnline();

        //Assert
        assertFalse(online, "isOnline() sollte false zurückgeben," +
                " weil die Netzwerkverbindung nicht aufgebaut wurde");

        //Verify
        verify(mockWeb3j).ethBlockNumber();
        verify(mockRequest).send();
    }

    @Test
    @DisplayName("Sollte true zurückgeben, wenn Signierung klappt (createTransaction)")
    void testCreateTransaction_SigningSuccess() {
        //Arrange
        String adresse = null;
        try {
            adresse = Credentials.create(ECKeyPair.create(Keys.createEcKeyPair().getPrivateKey())).getAddress();
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
        BigInteger nonce = BigInteger.ONE;
        BigInteger gasPrice = BigInteger.valueOf(987654321L);
        BigInteger gasLimit = BigInteger.valueOf(20000);
        BigInteger value = BigInteger.valueOf(123456789L);
        String data = null;

        boolean success = offlineTXCreator.createTransaction(nonce, gasPrice, gasLimit, adresse, value, data);
        assertTrue(success, "createTransaction() sollte bei Erfolg true zurückgeben.");
        assertFalse(offlineTXCreator.getSignedTransactions().isEmpty());

        // Überprüfe, ob die Signierfunktion der Wallet aufgerufen wurde
        verify(mockWallet).signTransaction(any(RawTransaction.class));
    }

    @Test
    @DisplayName("Sollte false zurückgeben, wenn Signierung fehlschlägt (createTransaction)")
    void testCreateTransaction_SigningFailure() {
        String adresse = null;
        try {
            adresse = Credentials.create(ECKeyPair.create(Keys.createEcKeyPair().getPrivateKey())).getAddress();
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
        BigInteger nonce = BigInteger.ONE;
        BigInteger gasPrice = BigInteger.valueOf(987654321L);
        BigInteger gasLimit = BigInteger.valueOf(20000);
        BigInteger value = BigInteger.valueOf(123456789L);
        String data = null;

        //Simuliere Fehler bei der signierung
        when(mockWallet.signTransaction(any(RawTransaction.class)))
                .thenThrow(new RuntimeException("Signierungsfehler simuliert"));

        //Act
        boolean transactionSuccess = offlineTXCreator
                .createTransaction(nonce, gasPrice, gasLimit, adresse, value, data);
        boolean emptyTransactionList = this.offlineTXCreator.getSignedTransactions().isEmpty();

        //Assert
        assertFalse(transactionSuccess,
                "createTransaction() sollte false sein wenn die signierung fehlschlägt");
        assertTrue(emptyTransactionList,
                "sollte true sein weil die Liste leer ist da die unerfolgreiche Transaktionen nicht hinzugefügt werden");

        //Verify
        verify(mockWallet).signTransaction(any(RawTransaction.class));
    }

    @Test
    @DisplayName("saveSignedTransactionsToJson sollte korrektes JSON in eine Datei schreiben")
    void testSaveSignedTransactionsToJson() throws IOException {
        String testDir = "test_output";
        String testFilePath = testDir + "/signed_transactions_test.json";

        // Verzeichnis anlegen, falls es nicht existiert
        Files.createDirectories(Paths.get(testDir));

        //Arrange
        offlineTXCreator
                .createTransaction(BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(21000), "0xabc123abc123abc123abc123abc123abc123abc1", BigInteger.valueOf(1000), null);

        //Act
        offlineTXCreator.saveSignedTransactionsToJson(testFilePath);

        //Assert
        File file = new File(testFilePath);
        assertTrue(file.exists(), "JSON sollte erstellt worden sein");

        String content = Files.readString(file.toPath());
        assertTrue(content.contains("0x"));

        //Cleaning
        file.delete();
    }

}
