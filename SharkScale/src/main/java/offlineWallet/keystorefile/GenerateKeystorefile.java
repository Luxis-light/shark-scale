package offlineWallet.keystorefile;

import org.web3j.crypto.CipherException;
import org.web3j.crypto.ECKeyPair;

import java.io.File;
import java.io.IOException;

/**
 * Definiert die Schnittstelle für die Generierung von Ethereum-Keystore-Dateien.
 * Diese Keystore-Dateien dienen der sicheren, passwortgeschützten Speicherung von privaten Schlüsseln.
 */
public interface GenerateKeystorefile {

    /**
     * Generiert eine neue Keystore-Datei aus einem gegebenen ECKeyPair und schützt sie mit einem Passwort.
     *
     * @param password             Das Passwort, das zum Verschlüsseln des privaten Schlüssels in der Keystore-Datei verwendet wird.
     *                             Muss sicher behandelt werden.
     * @param ecKeyPair            Das ECKeyPair, das den privaten und öffentlichen Schlüssel enthält, der gespeichert werden soll.
     * @param destinationDirectory Das Verzeichnis, in dem die Keystore-Datei gespeichert werden soll.
     *                             Muss ein gültiges, beschreibbares Verzeichnis sein.
     * @param includeTimestamp     Gibt an, ob der Dateiname einen Zeitstempel enthalten soll (empfohlen).
     * @return Der vollständige Pfad zur generierten Keystore-Datei.
     * @throws CipherException Wenn ein kryptographischer Fehler während der Verschlüsselung auftritt.
     * @throws IOException     Wenn ein E/A-Fehler beim Schreiben der Datei auftritt.
     */
    String generateKeystoreFile(String password, ECKeyPair ecKeyPair, File destinationDirectory, boolean includeTimestamp)
            throws CipherException, IOException;


}