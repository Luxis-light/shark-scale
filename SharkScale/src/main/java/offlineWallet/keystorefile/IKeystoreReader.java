package offlineWallet.keystorefile;

import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;

import java.io.File;
import java.io.IOException;

/**
 * Definiert die Schnittstelle zum Laden von Wallet-Credentials aus einer Datei.
 * Dies ermöglicht es, verschiedene Keystore-Formate (z.B. Standard Ethereum, PKCS#12)
 * auf eine einheitliche Weise zu laden.
 */
public interface IKeystoreReader {

    /**
     * Lädt die Credentials (privater/öffentlicher Schlüssel) aus einer gegebenen Datei.
     *
     * @param password   Das Passwort zur Entschlüsselung der Datei.
     * @param sourceFile Die Quelldatei, aus der die Wallet geladen wird.
     * @return Das geladene Credentials-Objekt.
     * @throws IOException     Wenn ein E/A-Fehler beim Lesen der Datei auftritt.
     * @throws CipherException Wenn ein kryptographischer Fehler während der Entschlüsselung auftritt.
     */
    Credentials loadCredentials(String password, File sourceFile) throws IOException, CipherException;
}