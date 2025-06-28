package offlineWallet.keystorefile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.web3j.crypto.*;

import java.io.File;
import java.io.IOException;

/**
 * Konkrete Implementierung des GenerateKeystorefile-Interfaces unter Verwendung von web3j.crypto.WalletUtils.
 */
public class KeystoreGenerator implements GenerateKeystorefile {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generiert eine neue Keystore-Datei aus einem gegebenen ECKeyPair und schützt sie mit einem Passwort.
     *
     * @param password             Das Passwort, das zum Verschlüsseln des privaten Schlüssels in der Keystore-Datei verwendet wird.
     *                             Muss sicher behandelt werden.
     * @param ecKeyPair            Das ECKeyPair, das den privaten und öffentlichen Schlüssel enthält, der gespeichert werden soll.
     * @param destinationDirectory Das Verzeichnis, in dem die Keystore-Datei gespeichert werden soll.
     *                             Muss ein gültiges, beschreibbares Verzeichnis sein.
     * @param filename             Optionaler Dateiname. Wenn null oder leer, wird ein Zeitstempel-basierter Standardname verwendet.
     * @return Der vollständige Pfad zur generierten Keystore-Datei.
     * @throws CipherException Wenn ein kryptographischer Fehler während der Verschlüsselung auftritt.
     * @throws IOException     Wenn ein E/A-Fehler beim Schreiben der Datei auftritt.
     */
    @Override
    public String generateKeystoreFile(String password, ECKeyPair ecKeyPair, File destinationDirectory, String filename) throws CipherException, IOException {
        if (filename == null || filename.trim().isEmpty()) {
            // Fall 1: Kein spezifischer Dateiname angegeben.
            // Verwende die web3j-Methode, die einen Zeitstempel-basierten Dateinamen generiert.
            // Diese Methode erstellt und schreibt die Datei und gibt den generierten Dateinamen zurück.
            return WalletUtils.generateWalletFile(password, ecKeyPair, destinationDirectory, true); // 'true' für useFullScrypt (Standard/Recommended)
        } else {
            // Fall 2: Ein benutzerdefinierter Dateiname wurde angegeben.
            // Da WalletUtils.generateWalletFile keinen Parameter für benutzerdefinierte Dateinamen hat,
            // müssen wir die WalletFile manuell erstellen und speichern.

            // Erstelle die WalletFile (hier als "light" Version, kann auch "standard" sein)
            WalletFile walletFile = Wallet.createLight(password, ecKeyPair); // Erstelle die WalletFile-Struktur im Speicher

            // Erstelle das vollständige Dateiobjekt für den Zielpfad und den benutzerdefinierten Dateinamen
            File destinationFile = new File(destinationDirectory, filename);

            // Schreibe die WalletFile als JSON in die angegebene Datei
            objectMapper.writeValue(destinationFile, walletFile);

            // Gib den vollständigen Pfad zur erstellten Datei zurück
            return destinationFile.getAbsolutePath();
        }
    }
    }

