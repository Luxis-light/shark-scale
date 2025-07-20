package offlineWallet;

import offlineWallet.keystorefile.GenerateKeystorefile;
import offlineWallet.keystorefile.IKeystoreReader;
import offlineWallet.keystorefile.KeystoreGenerator;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 *
 */
public class OfflineWallet implements GetWallet {




    private final Credentials credentials;
    private final GenerateKeystorefile generateKeystorefile;


    private final List<BalanceObserver> observers = new ArrayList<>();
    private BigInteger lastKnownBalance;



    public OfflineWallet(Credentials credentials, GenerateKeystorefile keystoreGenerator) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials cannot be null.");
        }
        if (keystoreGenerator == null) {
            throw new IllegalArgumentException("KeystoreGenerator cannot be null.");
        }
        this.credentials = credentials;
        this.generateKeystorefile = keystoreGenerator;
    }

    /**
     * Generiert eine neue, zufällige OfflineWallet.
     * Nutzt eine Standard-Implementierung für die Keystore-Generierung.
     */
    public static OfflineWallet generateNewWallet()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        ECKeyPair ecKeyPair = Keys.createEcKeyPair();
        Credentials newCredentials = Credentials.create(ecKeyPair);
        System.out.println("Neue Wallet generiert: " + newCredentials.getAddress());

        return new OfflineWallet(newCredentials, new KeystoreGenerator());
    }


    public void addBalanceObserver(BalanceObserver observer) {
        observers.add(observer);
    }

    public void removeBalanceObserver(BalanceObserver observer) {
        observers.remove(observer);
    }


    private void notifyObservers() throws IOException, InterruptedException, ExecutionException {
        for (BalanceObserver observer : observers) {

            observer.updateBalance(this, this.lastKnownBalance);
        }
    }

    /**
     * Lädt eine Wallet aus einer Keystore-Datei unter Verwendung eines spezifischen Readers.
     * Diese Methode ist nun flexibel für verschiedene Keystore-Formate.
     *
     * @param password             Das Passwort zur Entschlüsselung des Keystore.
     * @param sourceFile           Die Keystore-Datei.
     * @param keystoreReader       Die Implementierung, die zum Lesen der Datei verwendet wird.
     * @param keystoreGenerator    Eine Implementierung, die für zukünftige Speicherungen verwendet wird.
     * @return Eine Optional, die die OfflineWallet enthält, wenn sie erfolgreich geladen wurde.
     * @throws CipherException Wenn beim Entschlüsseln ein Fehler auftritt.
     * @throws IOException     Wenn beim Lesen der Datei ein Fehler auftritt.
     */
    public static Optional<OfflineWallet> loadWalletFromKeystore(String password, File sourceFile, IKeystoreReader keystoreReader, GenerateKeystorefile keystoreGenerator)
            throws CipherException, IOException {
        // Die Ladelogik wird an den übergebenen Reader delegiert
        Credentials loadedCredentials = keystoreReader.loadCredentials(password, sourceFile);
        if (loadedCredentials != null) {
            return Optional.of(new OfflineWallet(loadedCredentials, keystoreGenerator));
        }
        return Optional.empty();
    }

    /**
     * Gibt die Hexdresse in einem String an
     * @return die Hexadresse als String
     */
    public String getHexadresse() {

        return credentials.getAddress();
    }
    /**
     * Gibt den Public Key in einem String an
     *
     * @return der Key als String
     */
    public String getPublicKey() {
        return credentials.getEcKeyPair().getPublicKey().toString(16);
    }


    /**
     * Exportiert die Wallet sicher in eine Keystore-Datei unter Verwendung des injizierten Generators.
     *
     * @param password             Das Passwort zum Verschlüsseln des Keystore.
     * @param destinationDirectory Das Verzeichnis, in dem die Datei gespeichert werden soll.
     * @param fileName             Optionaler Dateiname (wird bei null durch Timestamp ergänzt/generiert).
     * @return Der vollständige Pfad zur generierten Keystore-Datei.
     * @throws CipherException Wenn beim Verschlüsseln ein Fehler auftritt.
     * @throws IOException     Wenn beim Schreiben der Datei ein Fehler auftritt.
     */
    public String exportWalletToKeystoreFile(String password, File destinationDirectory, String fileName)
            throws CipherException, IOException {
        // Delegiert die Speicherung an die injizierte Implementierung des Interfaces
        // Wir übergeben den internen ECKeyPair der Credentials
        return generateKeystorefile.generateKeystoreFile(
                password,
                credentials.getEcKeyPair(),
                destinationDirectory,
                fileName
        );
    }

    @Override
    public Credentials getCredentials() {
        return this.credentials;
    }

    @Override
    public String signTransaction(RawTransaction rawTransaction) {
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        return Numeric.toHexString(signedMessage);
    }


    @Override
    public BigInteger fetchBalance(Web3j web3j) throws IOException, InterruptedException, ExecutionException {
        BigInteger newBalance = web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).send().getBalance();

        // Nur benachrichtigen, wenn sich der Kontostand tatsächlich geändert hat
        if (!newBalance.equals(this.lastKnownBalance)) {
            this.lastKnownBalance = newBalance;
            System.out.println("Neuer Kontostand für " + getHexadresse() + ": " + newBalance);
            notifyObservers(); // Alle Beobachter informieren!
        }
        return newBalance;
    }


}
