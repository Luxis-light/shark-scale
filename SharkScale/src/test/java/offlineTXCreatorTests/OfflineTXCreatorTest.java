package offlineTXCreatorTests;

import offlineTXCreator.OfflineTXCreator;
import offlineWallet.GetWallet;
import offlineWallet.keystorefile.GenerateKeystorefile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OfflineTXCreatorTest {

    private GenerateKeystorefile mockKeystoreGenerator;
    private Credentials testCredentials;
    private GetWallet mockWallet;
    private Web3j mockWeb3j;
    private OfflineTXCreator offlineTXCreator;

    @BeforeEach
    public void setup() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, IOException, ExecutionException, InterruptedException {
        System.out.println("DEBUG: --- Setup-Methode gestartet ---");

        mockKeystoreGenerator = mock(GenerateKeystorefile.class);
        System.out.println("DEBUG: mockKeystoreGenerator erstellt. Ist es null? " + (mockKeystoreGenerator == null));

        testCredentials = Credentials.create(Keys.createEcKeyPair());
        System.out.println("DEBUG: testCredentials erstellt. Adresse: " + testCredentials.getAddress());

        mockWallet = mock(GetWallet.class);
        System.out.println("DEBUG: mockWallet erstellt. Ist es null? " + (mockWallet == null));
        when(mockWallet.getCredentials()).thenReturn(testCredentials);
        // Simuliert das Signieren einer Transaktion durch die Wallet.
        // WICHTIG: Verwende Numeric.toHexString, um den signierten Bytes-Array in einen Hex-String umzuwandeln.
        when(mockWallet.signTransaction(any(RawTransaction.class))).thenAnswer(invocation -> {
            RawTransaction rawTx = invocation.getArgument(0);
            return Numeric.toHexString(TransactionEncoder.signMessage(rawTx, testCredentials));
        });

        mockWeb3j = mock(Web3j.class);
        System.out.println("DEBUG: mockWeb3j erstellt. Ist es null? " + (mockWeb3j == null));

        // Mocke den Nonce-Abruf für den Konstruktor
        Request<String, EthGetTransactionCount> mockNonceRequest = mock(Request.class);
        EthGetTransactionCount mockEthGetTransactionCountResponse = mock(EthGetTransactionCount.class);
        doReturn(mockNonceRequest).when(mockWeb3j).ethGetTransactionCount(anyString(), eq(DefaultBlockParameterName.LATEST));
        when(mockNonceRequest.send()).thenReturn(mockEthGetTransactionCountResponse);
        when(mockEthGetTransactionCountResponse.getTransactionCount()).thenReturn(BigInteger.ZERO); // Standardmäßig Nonce 0 beim Start

        // Mocke ethSendRawTransaction für sendBatch Test
        Request<String, EthSendTransaction> mockSendTransactionRequest = mock(Request.class);
        EthSendTransaction mockEthSendTransactionResponse = mock(EthSendTransaction.class);

        // Verwende doReturn().when() für ethSendRawTransaction
        doReturn(mockSendTransactionRequest).when(mockWeb3j).ethSendRawTransaction(anyString());
        when(mockSendTransactionRequest.send()).thenReturn(mockEthSendTransactionResponse);
        when(mockEthSendTransactionResponse.hasError()).thenReturn(false); // Standardmäßig kein Fehler beim Senden
        when(mockEthSendTransactionResponse.getTransactionHash()).thenReturn("0xmockTransactionHash"); // Dummy-Hash

        System.out.println("DEBUG: Versuche OfflineTXCreator zu instantiieren mit mockWallet und mockWeb3j...");
        offlineTXCreator = new OfflineTXCreator(mockWallet, mockWeb3j);
        System.out.println("DEBUG: OfflineTXCreator erfolgreich instanziiert.");
        System.out.println("DEBUG: --- Setup-Methode beendet ---");
    }


    @Test
    @DisplayName("Sollte true zurückgeben, wenn das Netzwerk erreichbar ist (isOnline)")
    void testIsOnline_Positive() throws Exception {
        Request<String, EthBlockNumber> mockRequest = mock(Request.class);
        EthBlockNumber mockedEthBlockNumberResponse = mock(EthBlockNumber.class);

        doReturn(mockRequest).when(mockWeb3j).ethBlockNumber();
        when(mockRequest.send()).thenReturn(mockedEthBlockNumberResponse);
        when(mockedEthBlockNumberResponse.hasError()).thenReturn(false);

        boolean online = offlineTXCreator.isOnline();

        assertTrue(online, "isOnline() sollte true zurückgeben bei erfolgreicher Verbindung.");
        verify(mockWeb3j).ethBlockNumber();
        verify(mockRequest).send();
    }

    @Test
    @DisplayName("Sollte false zurückgeben, wenn das Netzwerk nicht erreichbar ist (isOnline)")
    void testIsOnline_Negative() throws Exception {
        Request<String, EthBlockNumber> mockRequest = mock(Request.class);

        doReturn(mockRequest).when(mockWeb3j).ethBlockNumber();
        when(mockRequest.send()).thenThrow(new IOException("Simulierter Netzwerk Fehler"));

        Boolean online = offlineTXCreator.isOnline();

        assertFalse(online, "isOnline() sollte false zurückgeben," +
                " weil die Netzwerkverbindung nicht aufgebaut wurde");

        verify(mockWeb3j).ethBlockNumber();
        verify(mockRequest).send();
    }

    @Test
    @DisplayName("Sollte true zurückgeben, wenn Signierung klappt (createTransaction)")
    void testCreateTransaction_SigningSuccess() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String recipientAddress = Credentials.create(Keys.createEcKeyPair()).getAddress();

        BigInteger gasPrice = BigInteger.valueOf(987654321L);
        BigInteger gasLimit = BigInteger.valueOf(20000);
        BigInteger value = BigInteger.valueOf(123456789L);
        String data = null;

        assertEquals(BigInteger.ZERO, offlineTXCreator.getCurrentNonce(), "Initial Nonce sollte 0 sein.");

        boolean success = offlineTXCreator.createTransaction(gasPrice, gasLimit, recipientAddress, value, data);
        assertTrue(success, "createTransaction() sollte bei Erfolg true zurückgeben.");
        assertFalse(offlineTXCreator.getSignedTransactions().isEmpty());

        assertEquals(BigInteger.ONE, offlineTXCreator.getCurrentNonce(), "Nonce sollte nach Transaktionserstellung inkrementiert werden.");

        verify(mockWallet).signTransaction(any(RawTransaction.class));
    }

    @Test
    @DisplayName("Sollte false zurückgeben, wenn Signierung fehlschlägt (createTransaction)")
    void testCreateTransaction_SigningFailure() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String recipientAddress = Credentials.create(Keys.createEcKeyPair()).getAddress();

        BigInteger gasPrice = BigInteger.valueOf(987654321L);
        BigInteger gasLimit = BigInteger.valueOf(20000);
        BigInteger value = BigInteger.valueOf(123456789L);
        String data = null;

        BigInteger initialNonce = offlineTXCreator.getCurrentNonce();

        when(mockWallet.signTransaction(any(RawTransaction.class)))
                .thenThrow(new RuntimeException("Signierungsfehler simuliert"));

        boolean transactionSuccess = offlineTXCreator
                .createTransaction(gasPrice, gasLimit, recipientAddress, value, data);
        boolean emptyTransactionList = this.offlineTXCreator.getSignedTransactions().isEmpty();

        assertFalse(transactionSuccess,
                "createTransaction() sollte false sein wenn die signierung fehlschlägt");
        assertTrue(emptyTransactionList,
                "sollte true sein weil die Liste leer ist da die unerfolgreiche Transaktionen nicht hinzugefügt werden");
        assertEquals(initialNonce, offlineTXCreator.getCurrentNonce(), "Nonce sollte bei Fehlschlag NICHT inkrementiert werden.");

        verify(mockWallet).signTransaction(any(RawTransaction.class));
    }

    @Test
    @DisplayName("saveSignedTransactionsToJson sollte korrektes JSON in eine Datei schreiben")
    void testSaveSignedTransactionsToJson() throws IOException {
        String testDir = "test_output";
        String testFilePath = testDir + "/signed_transactions_test.json";

        Files.createDirectories(Paths.get(testDir));

        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), "0xabc123abc123abc123abc123abc123abc123abc1", BigInteger.valueOf(1000), null);

        offlineTXCreator.saveSignedTransactionsToJson(testFilePath);

        File file = new File(testFilePath);
        assertTrue(file.exists(), "JSON sollte erstellt worden sein");

        String content = Files.readString(file.toPath());
        assertTrue(content.contains("0x"), "Inhalt sollte '0x' für Hex-String enthalten.");

        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(Paths.get(testDir));
    }

    @Test
    @DisplayName("resyncNonce sollte die aktuelle Nonce vom Netzwerk abrufen und setzen")
    void testResyncNonce() throws IOException, InterruptedException, ExecutionException {
        // Arrange
        BigInteger expectedNonce = BigInteger.valueOf(10);

        Request<String, EthGetTransactionCount> mockNonceRequest = mock(Request.class);
        EthGetTransactionCount mockEthGetTransactionCountResponse = mock(EthGetTransactionCount.class);

        // ZURÜCKSETZEN DES MOCKS, um nur die Aufrufe in diesem Test zu zählen
        reset(mockWeb3j);
        doReturn(mockNonceRequest).when(mockWeb3j).ethGetTransactionCount(eq(testCredentials.getAddress()), eq(DefaultBlockParameterName.LATEST));
        when(mockNonceRequest.send()).thenReturn(mockEthGetTransactionCountResponse);
        when(mockEthGetTransactionCountResponse.getTransactionCount()).thenReturn(expectedNonce);

        // Act
        offlineTXCreator.resyncNonce();

        // Assert
        assertEquals(expectedNonce, offlineTXCreator.getCurrentNonce(), "resyncNonce sollte die Nonce vom Netzwerk abrufen und aktualisieren.");
        verify(mockWeb3j, times(1)).ethGetTransactionCount(eq(testCredentials.getAddress()), eq(DefaultBlockParameterName.LATEST));
        verify(mockNonceRequest, times(1)).send();
    }

    @Test
    @DisplayName("sendBatch sollte alle signierten Transaktionen senden und Hashes zurückgeben")
    void testSendBatch() throws Exception {
        // Arrange
        // Mocks für sendSignedTransaction sind bereits im setup() konfiguriert
        // Sie geben "0xmockTransactionHash" zurück

        // Erzeuge zufällige Empfängeradressen korrekt:
        String recipientAddress1 = Credentials.create(Keys.createEcKeyPair()).getAddress();
        String recipientAddress2 = Credentials.create(Keys.createEcKeyPair()).getAddress();
        String recipientAddress3 = Credentials.create(Keys.createEcKeyPair()).getAddress();

        // Füge dem TXCreator signierte Transaktionen hinzu (Nonce wird intern inkrementiert)
        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), recipientAddress1, BigInteger.ONE, null);
        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), recipientAddress2, BigInteger.ONE, null);
        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), recipientAddress3, BigInteger.ONE, null);

        // Act
        ArrayList<String> sentHashes = offlineTXCreator.sendBatch();

        // Assert
        assertFalse(sentHashes.isEmpty(), "sendBatch sollte Hashes zurückgeben.");
        assertEquals(3, sentHashes.size(), "Es sollten 3 Transaktions-Hashes zurückgegeben werden.");
        // Prüfen, ob die richtigen Dummy-Hashes zurückgegeben wurden
        assertTrue(sentHashes.stream().allMatch(h -> h.equals("0xmockTransactionHash")), "Alle Hashes sollten dem gemockten Wert entsprechen.");

        // Überprüfen, ob sendSignedTransaction für jede Transaktion aufgerufen wurde
        verify(offlineTXCreator, times(3)).sendSignedTransaction(anyString());

        assertEquals(3, offlineTXCreator.getSignedTransactions().size(), "Interne Liste sollte unverändert bleiben, da Parameter übergeben.");
    }
}
