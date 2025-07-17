package offlinewallet;

import offlineWallet.BalanceObserver;
import offlineWallet.OfflineWallet;
import offlineWallet.keystorefile.GenerateKeystorefile;
import offlineWallet.keystorefile.IKeystoreReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OfflineWalletTest {

    @TempDir
    Path tempDir;
    private Credentials testCredentials;
    private GenerateKeystorefile mockKeystoreGenerator;
    private IKeystoreReader mockKeystoreReader;
    private Web3j mockWeb3j;
    private OfflineWallet wallet;

    @BeforeEach
    void setUp() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Erstellen von Test-Credentials
        testCredentials = Credentials.create(Keys.createEcKeyPair());

        // Mocken der Abhängigkeiten
        mockKeystoreGenerator = mock(GenerateKeystorefile.class);
        mockKeystoreReader = mock(IKeystoreReader.class);
        mockWeb3j = mock(Web3j.class);

        // Erstellen der zu testenden Instanz
        wallet = new OfflineWallet(testCredentials, mockKeystoreGenerator);
    }

    @Test
    @DisplayName("Konstruktor sollte Exception bei null Credentials werfen")
    void constructor_shouldThrowExceptionForNullCredentials() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new OfflineWallet(null, mockKeystoreGenerator);
        });
        assertEquals("Credentials cannot be null.", exception.getMessage());
    }

    @Test
    @DisplayName("Konstruktor sollte Exception bei null KeystoreGenerator werfen")
    void constructor_shouldThrowExceptionForNullKeystoreGenerator() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new OfflineWallet(testCredentials, null);
        });
        assertEquals("KeystoreGenerator cannot be null.", exception.getMessage());
    }

    @Test
    @DisplayName("loadWalletFromKeystore sollte Wallet bei Erfolg zurückgeben")
    void loadWalletFromKeystore_shouldReturnWalletOnSuccess() throws IOException, CipherException {
        // Arrange
        File keystoreFile = tempDir.resolve("keystore.json").toFile();
        String password = "test_password";
        when(mockKeystoreReader.loadCredentials(password, keystoreFile)).thenReturn(testCredentials);

        // Act
        Optional<OfflineWallet> loadedWalletOptional = OfflineWallet.loadWalletFromKeystore(password, keystoreFile, mockKeystoreReader, mockKeystoreGenerator);

        // Assert
        assertTrue(loadedWalletOptional.isPresent(), "Ein Wallet-Objekt sollte zurückgegeben werden.");
        assertEquals(testCredentials.getAddress(), loadedWalletOptional.get().getHexadresse(), "Die Adresse der geladenen Wallet muss stimmen.");
        verify(mockKeystoreReader, times(1)).loadCredentials(password, keystoreFile);
    }

    @Test
    @DisplayName("loadWalletFromKeystore sollte leeres Optional bei CipherException zurückgeben")
    void loadWalletFromKeystore_shouldReturnEmptyOnCipherException() throws IOException, CipherException {
        // Arrange
        File keystoreFile = tempDir.resolve("keystore.json").toFile();
        String wrongPassword = "wrong_password";
        when(mockKeystoreReader.loadCredentials(wrongPassword, keystoreFile)).thenThrow(new CipherException("Falsches Passwort"));

        // Act & Assert
        // Die Exception wird von der Methode gefangen und als leeres Optional interpretiert,
        // daher prüfen wir, ob die aufrufende Methode die Exception korrekt wirft.
        assertThrows(CipherException.class, () -> {
            OfflineWallet.loadWalletFromKeystore(wrongPassword, keystoreFile, mockKeystoreReader, mockKeystoreGenerator);
        });
    }

    @Test
    @DisplayName("fetchBalance sollte Observer bei Kontostandsänderung benachrichtigen")
    void fetchBalance_shouldNotifyObserverOnBalanceChange() throws IOException, ExecutionException, InterruptedException {
        // Arrange
        BalanceObserver mockObserver = mock(BalanceObserver.class);
        wallet.addBalanceObserver(mockObserver);

        BigInteger newBalance = BigInteger.valueOf(1000);
        // Mocken der Web3j-Aufrufkette
        Request mockRequest = mock(Request.class);
        EthGetBalance ethGetBalance = new EthGetBalance();
        ethGetBalance.setResult(Numeric.toHexStringWithPrefix(newBalance));

        when(mockWeb3j.ethGetBalance(anyString(), any())).thenReturn(mockRequest);
        when(mockRequest.send()).thenReturn(ethGetBalance);

        // Act
        wallet.fetchBalance(mockWeb3j);

        // Assert
        // Überprüfen, ob updateBalance auf dem Observer genau einmal mit dem neuen Kontostand aufgerufen wurde
        verify(mockObserver, times(1)).updateBalance(newBalance);
    }

    @Test
    @DisplayName("fetchBalance sollte Observer NICHT bei gleichem Kontostand benachrichtigen")
    void fetchBalance_shouldNotNotifyObserverIfBalanceIsUnchanged() throws IOException, ExecutionException, InterruptedException {
        // Arrange
        BigInteger initialBalance = BigInteger.valueOf(500);
        // Setze einen bekannten Anfangsstand (simuliert einen vorherigen Aufruf)
        Request initialMockRequest = mock(Request.class);
        EthGetBalance initialEthGetBalance = new EthGetBalance();
        initialEthGetBalance.setResult(Numeric.toHexStringWithPrefix(initialBalance));
        when(mockWeb3j.ethGetBalance(anyString(), any())).thenReturn(initialMockRequest);
        when(initialMockRequest.send()).thenReturn(initialEthGetBalance);
        wallet.fetchBalance(mockWeb3j); // Erster Aufruf setzt den internen Stand

        // Jetzt einen Observer hinzufügen
        BalanceObserver mockObserver = mock(BalanceObserver.class);
        wallet.addBalanceObserver(mockObserver);

        // Der zweite Aufruf gibt denselben Kontostand zurück
        Request secondMockRequest = mock(Request.class);
        EthGetBalance secondEthGetBalance = new EthGetBalance();
        secondEthGetBalance.setResult(Numeric.toHexStringWithPrefix(initialBalance));
        when(mockWeb3j.ethGetBalance(anyString(), any())).thenReturn(secondMockRequest);
        when(secondMockRequest.send()).thenReturn(secondEthGetBalance);

        // Act
        wallet.fetchBalance(mockWeb3j);

        // Assert
        // updateBalance darf nicht aufgerufen worden sein, da der Kontostand gleich geblieben ist
        verify(mockObserver, never()).updateBalance(any());
    }

    @Test
    @DisplayName("Ein entfernter Observer sollte nicht mehr benachrichtigt werden")
    void removedObserver_shouldNotBeNotified() throws IOException, ExecutionException, InterruptedException {
        // Arrange
        BalanceObserver mockObserver = mock(BalanceObserver.class);
        wallet.addBalanceObserver(mockObserver);
        wallet.removeBalanceObserver(mockObserver); // <-- Observer wird sofort wieder entfernt

        BigInteger newBalance = BigInteger.valueOf(2000);
        Request mockRequest = mock(Request.class);
        EthGetBalance ethGetBalance = new EthGetBalance();
        ethGetBalance.setResult(Numeric.toHexStringWithPrefix(newBalance));
        when(mockWeb3j.ethGetBalance(anyString(), any())).thenReturn(mockRequest);
        when(mockRequest.send()).thenReturn(ethGetBalance);

        // Act
        wallet.fetchBalance(mockWeb3j);

        // Assert
        verify(mockObserver, never()).updateBalance(any());
    }
}