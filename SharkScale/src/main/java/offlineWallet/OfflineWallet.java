package offlineWallet;

import offlineWallet.keystorefile.GenerateKeystorefile;
import offlineWallet.keystorefile.KeystoreGenerator;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Optional;

public class OfflineWallet implements GetWallet {




    private final Credentials credentials;
    private final GenerateKeystorefile generateKeystorefile;

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

    /**
     * Lädt eine Wallet aus einer Keystore-Datei mit einem Passwort.
     * Diese Methode ist statisch, da sie keine bestehende OfflineWallet-Instanz benötigt,
     * sondern eine neue erzeugt. Sie ist auch nicht Teil des GenerateKeystorefile-Interfaces,
     * da dieses nur für die Generierung (Speicherung) zuständig ist, nicht für das Laden.
     *
     * @param password          Das Passwort zur Entschlüsselung des Keystore.
     * @param sourceFile        Die Keystore-Datei.
     * @param keystoreGenerator Eine Implementierung des GenerateKeystorefile-Interfaces,
     *                          die für die *zukünftige* Speicherung der geladenen Wallet verwendet wird.
     * @return Eine Optional, die die OfflineWallet enthält, wenn sie erfolgreich geladen wurde.
     * @throws CipherException Wenn beim Entschlüsseln ein Fehler auftritt.
     * @throws IOException     Wenn beim Lesen der Datei ein Fehler auftritt.
     */
    public static Optional<OfflineWallet> loadWalletFromKeystore(String password, File sourceFile, GenerateKeystorefile keystoreGenerator)
            throws CipherException, IOException {
        Credentials loadedCredentials = org.web3j.crypto.WalletUtils.loadCredentials(password, sourceFile);
        if (loadedCredentials != null) {
            return Optional.of(new OfflineWallet(loadedCredentials, keystoreGenerator));
        }
        return Optional.empty();
    }

    /**
     * Gibt die Hexdresse in einem String an
     *
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
                true
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



}
