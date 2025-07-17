package offlineTXCreatorTests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import offlineTXCreator.OfflineTXCreator;
import offlineWallet.GetWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OfflineTXCreatorTest {

    private Credentials testCredentials;
    private GetWallet mockWallet;
    private Web3j mockWeb3j;
    private OfflineTXCreator offlineTXCreator;
    private final long CHAIN_ID = 1L; // Standard-Chain-ID für Tests

    // @TempDir wird von JUnit 5 bereitgestellt, um ein temporäres Verzeichnis zu erstellen
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, IOException, ExecutionException, InterruptedException {
        testCredentials = Credentials.create(Keys.createEcKeyPair());

        mockWallet = mock(GetWallet.class);
        when(mockWallet.getCredentials()).thenReturn(testCredentials);

        mockWeb3j = mock(Web3j.class);

        // Mock für die Chain-ID
        Request mockChainIdRequest = mock(Request.class);
        when(mockWeb3j.ethChainId()).thenReturn(mockChainIdRequest);
        when(mockChainIdRequest.send()).thenReturn(new org.web3j.protocol.core.methods.response.EthChainId() {{
            setResult(String.valueOf(CHAIN_ID));
        }});

        // Mock für den Nonce-Abruf im Konstruktor
        Request<String, EthGetTransactionCount> mockNonceRequest = mock(Request.class);
        EthGetTransactionCount mockEthGetTransactionCountResponse = mock(EthGetTransactionCount.class);
        doReturn(mockNonceRequest).when(mockWeb3j).ethGetTransactionCount(anyString(), eq(DefaultBlockParameterName.LATEST));
        when(mockNonceRequest.send()).thenReturn(mockEthGetTransactionCountResponse);
        when(mockEthGetTransactionCountResponse.getTransactionCount()).thenReturn(BigInteger.ZERO);

        // Standard-Mock für das Senden von Transaktionen
        Request<String, EthSendTransaction> mockSendTxRequest = mock(Request.class);
        EthSendTransaction mockSendTxResponse = mock(EthSendTransaction.class);
        doReturn(mockSendTxRequest).when(mockWeb3j).ethSendRawTransaction(anyString());
        when(mockSendTxRequest.send()).thenReturn(mockSendTxResponse);
        when(mockSendTxResponse.hasError()).thenReturn(false);
        when(mockSendTxResponse.getTransactionHash()).thenReturn("0xmockTransactionHash");

        // Initialisierung des Creators, der getestet wird
        offlineTXCreator = new OfflineTXCreator(mockWallet, mockWeb3j);
    }


    @Test
    @DisplayName("Sollte nur Transaktionen laden, die der eigenen Wallet gehören")
    void testLoadTransactionsFromJson_OwnerValidation() throws IOException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Arrange
        Credentials foreignCredentials = Credentials.create(Keys.createEcKeyPair());
        String ownAddress = testCredentials.getAddress();
        String foreignAddress = foreignCredentials.getAddress();

        // Erstelle einen Job, der zur Wallet gehört
        OfflineTXCreator.TransactionJob ownJob = new OfflineTXCreator.TransactionJob(
                ownAddress, BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(21000), "0xto1", BigInteger.ONE, null, "0xsigned1"
        );
        // Erstelle einen Job, der zu einer fremden Wallet gehört
        OfflineTXCreator.TransactionJob foreignJob = new OfflineTXCreator.TransactionJob(
                foreignAddress, BigInteger.TWO, BigInteger.TEN, BigInteger.valueOf(21000), "0xto2", BigInteger.ONE, null, "0xsigned2"
        );

        List<OfflineTXCreator.TransactionJob> jobsToSave = new ArrayList<>(List.of(ownJob, foreignJob));

        // Speichere beide Jobs in einer JSON-Datei
        File testFile = tempDir.resolve("mixed_transactions.json").toFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write(gson.toJson(jobsToSave));
        }

        // Act
        // Lade die Transaktionen. Der Creator sollte nur seine eigene laden.
        boolean success = offlineTXCreator.loadTransactionsFromJsonAndDelete(testFile.getAbsolutePath());

        // Assert
        assertTrue(success, "Das Laden der Datei sollte erfolgreich sein.");
        assertEquals(1, offlineTXCreator.getPendingTransactionJobs().size(), "Es sollte nur eine Transaktion geladen werden.");

        OfflineTXCreator.TransactionJob loadedJob = offlineTXCreator.getPendingTransactionJobs().get(0);
        assertEquals(ownAddress.toLowerCase(), loadedJob.ownerAdress().toLowerCase(), "Die geladene Transaktion muss die eigene Adresse als Besitzer haben.");
        assertEquals(ownJob.signedHex(), loadedJob.signedHex(), "Der signierte Hex-String der geladenen Transaktion muss korrekt sein.");

        assertFalse(testFile.exists(), "Die Quelldatei sollte nach dem Laden gelöscht werden.");
    }

    // --- NEUER TEST ---
    @Test
    @DisplayName("Sollte bei createTransaction die korrekte ownerAddress setzen")
    void testCreateTransaction_SetsCorrectOwnerAddress() {
        // Arrange
        String recipientAddress = "0x1234567890123456789012345678901234567890";

        // Act
        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), recipientAddress, BigInteger.ONE, null);

        // Assert
        assertEquals(1, offlineTXCreator.getPendingTransactionJobs().size());
        OfflineTXCreator.TransactionJob createdJob = offlineTXCreator.getPendingTransactionJobs().get(0);

        assertNotNull(createdJob.ownerAdress(), "ownerAddress darf nicht null sein.");
        assertEquals(testCredentials.getAddress().toLowerCase(), createdJob.ownerAdress().toLowerCase(), "Die ownerAddress muss der Adresse des Creators entsprechen.");
    }

    // --- NEUER TEST ---
    @Test
    @DisplayName("Nonce-Korrektur sollte nur für eigene Transaktionen greifen")
    void testNonceCorrectionLogic_OnOwnedTransaction() throws Exception {
        // Arrange: Erstelle eine Transaktion, die einen Nonce-Fehler provozieren wird.
        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), "0xto", BigInteger.ONE, null);

        // --- Mocking-Setup für den Fehlerfall ---
        // 1. Simuliere einen "nonce too low" Fehler für den ersten Sendeversuch
        EthSendTransaction errorResponse = mock(EthSendTransaction.class);
        when(errorResponse.hasError()).thenReturn(true);
        when(errorResponse.getError()).thenReturn(new org.web3j.protocol.core.Response.Error(-32000, "nonce too low"));

        // --- Mocking-Setup für den Erfolgsfall nach der Korrektur ---
        // 2. Erstelle einen separaten Mock für die erfolgreiche Antwort
        EthSendTransaction successResponse = mock(EthSendTransaction.class);
        when(successResponse.hasError()).thenReturn(false);
        when(successResponse.getTransactionHash()).thenReturn("0xcorrectedHash");

        // 3. Mocke den Web3j-Aufruf so, dass er ZUERST den Fehler und DANN den Erfolg zurückgibt
        Request mockRequestSendTx = mock(Request.class);
        when(mockWeb3j.ethSendRawTransaction(anyString())).thenReturn(mockRequestSendTx);
        when(mockRequestSendTx.send())
                .thenReturn(errorResponse)      // Erster Aufruf gibt Fehler zurück
                .thenReturn(successResponse);   // Zweiter Aufruf gibt Erfolg zurück

        // --- Mocking-Setup für die Nonce-Neusynchronisierung ---
        // 4. Gib eine höhere Nonce für den Korrekturmechanismus zurück
        BigInteger correctedNonce = BigInteger.valueOf(5);
        EthGetTransactionCount correctedNonceResponse = mock(EthGetTransactionCount.class);
        when(correctedNonceResponse.getTransactionCount()).thenReturn(correctedNonce);

        Request mockRequestGetNonce = mock(Request.class);
        when(mockWeb3j.ethGetTransactionCount(anyString(), any())).thenReturn(mockRequestGetNonce);
        when(mockRequestGetNonce.send()).thenReturn(correctedNonceResponse);


        // Act
        ArrayList<String> sentHashes = offlineTXCreator.sendBatch();

        // Assert
        assertEquals(1, sentHashes.size(), "Ein Transaktions-Hash sollte zurückgegeben werden.");
        assertEquals("0xcorrectedHash", sentHashes.get(0), "Der Hash der korrigierten Transaktion sollte zurückgegeben werden.");

        // Überprüfe, ob die Nonce am Ende korrekt inkrementiert wurde
        assertEquals(correctedNonce.add(BigInteger.ONE), offlineTXCreator.getCurrentNonce(), "Die Nonce sollte nach der Korrektur auf 6 stehen.");
    }

    // Bestehende Tests bleiben erhalten und sind weiterhin wertvoll
    @Test
    @DisplayName("Sollte true zurückgeben, wenn Signierung klappt (createTransaction)")
    void testCreateTransaction_SigningSuccess() {
        assertEquals(BigInteger.ZERO, offlineTXCreator.getCurrentNonce(), "Initial Nonce sollte 0 sein.");

        boolean success = offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), "0xto", BigInteger.ONE, null);

        assertTrue(success, "createTransaction() sollte bei Erfolg true zurückgeben.");
        assertFalse(offlineTXCreator.getPendingTransactionJobs().isEmpty(), "Die Liste der Jobs sollte nicht leer sein.");
        assertEquals(BigInteger.ONE, offlineTXCreator.getCurrentNonce(), "Nonce sollte nach der Erstellung inkrementiert werden.");
    }

    @Test
    @DisplayName("Sollte false zurückgeben, wenn Signierung fehlschlägt (createTransaction)")
    void testCreateTransaction_SigningFailure() throws IOException {
        // --- KORREKTUR: Der Konstruktor soll erfolgreich sein ---
        // Die Initialisierung von offlineTXCreator aus der setup()-Methode ist ausreichend.
        // Wir brauchen keine Re-Initialisierung mehr.

        BigInteger initialNonce = offlineTXCreator.getCurrentNonce();

        // --- KORREKTUR: Simuliere den Fehler erst JETZT, direkt vor dem Aufruf ---
        // Wir weisen den Mock an, erst ab diesem Punkt einen Fehler zu werfen, wenn
        // die createTransaction-Methode versucht, die Credentials zum Signieren abzurufen.
        when(mockWallet.getCredentials()).thenThrow(new RuntimeException("Simulierter Signierfehler"));

        // Act: Rufen Sie die Methode auf, die jetzt fehlschlagen wird.
        boolean success = offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), "0xto", BigInteger.ONE, null);

        // Assert: Prüfen Sie das erwartete Verhalten nach dem Fehler.
        assertFalse(success, "createTransaction() sollte bei einem Signierfehler false zurückgeben.");
        assertTrue(offlineTXCreator.getPendingTransactionJobs().isEmpty(), "Bei einem Fehler darf kein Job zur Liste hinzugefügt werden.");
        assertEquals(initialNonce, offlineTXCreator.getCurrentNonce(), "Die Nonce darf bei einem Fehler nicht inkrementiert werden.");
    }

    @Test
    @DisplayName("sendBatch sollte eine leere Liste zurückgeben, wenn keine Transaktionen vorhanden sind")
    void testSendBatch_WithNoTransactions() throws Exception {
        // Arrange
        assertTrue(offlineTXCreator.getPendingTransactionJobs().isEmpty(), "Die Liste sollte anfangs leer sein.");

        // Act
        ArrayList<String> sentHashes = offlineTXCreator.sendBatch();

        // Assert
        assertNotNull(sentHashes, "Die zurückgegebene Liste darf nicht null sein.");
        assertTrue(sentHashes.isEmpty(), "Die Liste der Hashes sollte leer sein, da nichts gesendet wurde.");
        verify(mockWeb3j, never()).ethSendRawTransaction(anyString()); // Es darf kein Sendeversuch stattgefunden haben
    }

    @Test
    @DisplayName("Sollte eine Transaktion mit einem Datenfeld korrekt erstellen")
    void testCreateTransaction_WithData() {
        // Arrange
        String recipientAddress = "0x1234567890123456789012345678901234567890";
        String data = "0xdeadbeef"; // Beispiel-Daten

        // Act
        boolean success = offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(30000), recipientAddress, BigInteger.ZERO, data);

        // Assert
        assertTrue(success);
        OfflineTXCreator.TransactionJob job = offlineTXCreator.getPendingTransactionJobs().get(0);

        assertEquals(data, job.data(), "Das Datenfeld im Job muss mit den übergebenen Daten übereinstimmen.");
    }

    @Test
    @DisplayName("resyncNonce sollte eine Exception werfen, wenn das Netzwerk nicht erreichbar ist")
    void testResyncNonce_WhenNetworkIsDown() throws IOException {
        // Arrange
        reset(mockWeb3j);

        // Erstelle einen Mock für das Request-Objekt
        Request mockRequest = mock(Request.class);

        // Simuliere, dass die .send()-Methode die IOException wirft
        when(mockRequest.send()).thenThrow(new IOException("Netzwerk nicht erreichbar"));

        // Simuliere, dass ethGetTransactionCount unseren gemockten Request zurückgibt
        when(mockWeb3j.ethGetTransactionCount(anyString(), any(DefaultBlockParameterName.class)))
                .thenReturn(mockRequest);

        // Act & Assert
        // --- KORREKTUR: Wir erwarten direkt die IOException ---
        assertThrows(IOException.class, () -> {
            offlineTXCreator.resyncNonce();
        }, "Eine IOException sollte geworfen werden, wenn die Nonce nicht synchronisiert werden kann.");

        // Überprüfen, dass die Kette korrekt aufgerufen wurde
        verify(mockWeb3j).ethGetTransactionCount(anyString(), any(DefaultBlockParameterName.class));
        verify(mockRequest).send();
    }


    @Test
    @DisplayName("Sollte das Speichern und Laden einer leeren Transaktionsliste korrekt behandeln")
    void testSaveAndLoad_EmptyTransactionList() throws IOException {
        // Arrange
        // --- KORREKTUR: Wir übergeben das Verzeichnis, nicht die Datei ---
        File directory = tempDir.toFile();
        String filename = "empty_transactions.json";
        File finalTestFile = new File(directory, filename);

        // Speichere die leere Liste
        offlineTXCreator.saveAndClearTransactionsToJson(directory, filename);
        assertTrue(finalTestFile.exists(), "Die Datei sollte erstellt worden sein.");

        // Act
        // Lade aus der gerade erstellten Datei
        offlineTXCreator.loadTransactionsFromJsonAndDelete(finalTestFile.getAbsolutePath());

        // Assert
        assertTrue(offlineTXCreator.getPendingTransactionJobs().isEmpty(), "Die Job-Liste sollte nach dem Laden einer leeren Datei weiterhin leer sein.");
        assertFalse(finalTestFile.exists(), "Die Quelldatei sollte auch dann gelöscht werden, wenn sie leer war.");
    }

    @Test
    @DisplayName("isNonceError sollte alle bekannten Nonce-Fehlermeldungen erkennen")
    void isNonceError_shouldDetectAllNonceErrorTypes() throws Exception {
        // Arrange
        // Hole die private Methode 'isNonceError' über Reflektion
        Method isNonceErrorMethod = OfflineTXCreator.class.getDeclaredMethod("isNonceError", RuntimeException.class);
        // Mache sie zugänglich, um die 'private'-Beschränkung zu umgehen
        isNonceErrorMethod.setAccessible(true);

        RuntimeException nonceTooLow = new RuntimeException("... nonce too low ...");
        RuntimeException alreadyKnown = new RuntimeException("... already known ...");
        RuntimeException invalidNonce = new RuntimeException("... invalid nonce ...");
        RuntimeException otherError = new RuntimeException("Ein ganz anderer Fehler");

        // Act & Assert
        // Rufe die private Methode über isNonceErrorMethod.invoke() auf
        assertTrue((Boolean) isNonceErrorMethod.invoke(offlineTXCreator, nonceTooLow), "'nonce too low' sollte erkannt werden.");
        assertTrue((Boolean) isNonceErrorMethod.invoke(offlineTXCreator, alreadyKnown), "'already known' sollte erkannt werden.");
        assertTrue((Boolean) isNonceErrorMethod.invoke(offlineTXCreator, invalidNonce), "'invalid nonce' sollte erkannt werden.");
        assertFalse((Boolean) isNonceErrorMethod.invoke(offlineTXCreator, otherError), "Ein anderer Fehler darf nicht als Nonce-Fehler gewertet werden.");
    }

    @Test
    @DisplayName("sendBatch sollte eine Exception bei einem nicht behebbaren Fehler werfen")
    void sendBatch_shouldThrowExceptionOnNonRecoverableError() {
        // Arrange
        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), "0xto", BigInteger.ONE, null);

        // Simuliere einen Fehler, der KEIN Nonce-Fehler ist
        Request mockRequest = mock(Request.class);
        EthSendTransaction errorResponse = new EthSendTransaction();
        errorResponse.setError(new org.web3j.protocol.core.Response.Error(-1, "Insufficient funds"));

        try {
            when(mockRequest.send()).thenReturn(errorResponse);
            when(mockWeb3j.ethSendRawTransaction(anyString())).thenReturn(mockRequest);
        } catch (IOException e) {
            fail("Mocking sollte keine IOException werfen", e);
        }

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            offlineTXCreator.sendBatch();
        }, "Eine allgemeine Exception sollte bei einem nicht behebbaren Fehler geworfen werden.");

        assertTrue(exception.getMessage().contains("Nicht behebbarer Fehler"), "Die Fehlermeldung sollte den Typ des Fehlers angeben.");
    }

    @Test
    @DisplayName("createTransaction mit manuellem Nonce sollte korrekt funktionieren")
    void createTransaction_withManualNonce_shouldSucceed() {
        // Arrange
        BigInteger manualNonce = BigInteger.valueOf(42);
        String recipient = "0xrecipient";
        BigInteger value = BigInteger.valueOf(123);

        // Act
        boolean success = offlineTXCreator.createTransaction(manualNonce, BigInteger.TEN, BigInteger.valueOf(21000), recipient, value, null);

        // Assert
        assertTrue(success, "Die Transaktionserstellung sollte erfolgreich sein.");
        assertEquals(1, offlineTXCreator.getPendingTransactionJobs().size(), "Ein Job sollte erstellt worden sein.");

        OfflineTXCreator.TransactionJob job = offlineTXCreator.getPendingTransactionJobs().get(0);
        assertEquals(manualNonce, job.nonce(), "Die Nonce des Jobs muss der manuell übergebenen Nonce entsprechen.");

        // Wichtig: Die interne Nonce des Creators darf sich bei manueller Angabe NICHT ändern!
        assertEquals(BigInteger.ZERO, offlineTXCreator.getCurrentNonce(), "Die interne Nonce des Creators sollte unverändert bleiben.");
    }

    @Test
    @DisplayName("getSignedTransactions sollte eine Liste der Hex-Strings zurückgeben")
    void getSignedTransactions_shouldReturnListOfHexStrings() {
        // Arrange
        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), "0xto1", BigInteger.ONE, null);
        offlineTXCreator.createTransaction(BigInteger.TEN, BigInteger.valueOf(21000), "0xto2", BigInteger.TWO, null);

        // Act
        List<String> signedHexes = offlineTXCreator.getSignedTransactions();

        // Assert
        assertNotNull(signedHexes);
        assertEquals(2, signedHexes.size(), "Die Liste sollte zwei Einträge haben.");
        // Prüfen, ob die Einträge tatsächlich Hex-Strings sind
        assertTrue(signedHexes.get(0).startsWith("0x"));
        assertTrue(signedHexes.get(1).startsWith("0x"));
    }

}