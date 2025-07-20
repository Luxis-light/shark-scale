package offlineTXCreator;

import java.util.ArrayList;

/**
 * Dieses Interface definiert die notwendigen Methoden für eine Netzwerkverbindung, um Transaktionen an ein Blockchain-Netzwerk zu senden.
 */
public interface INetworkConnection {

    /**
     * Überprüft den aktuellen Verbindungsstatus zum Blockchain-Netzwerk.
     *
     * @return true, wenn eine Verbindung zum Netzwerk besteht; false sonst.
     */
    boolean isOnline();

    /**
     * Versucht, eine Liste von gesammelten, signierten Transaktionen an das Netzwerk zu senden.
     *

     * @return Eine Liste von Transaktions-Hashes für die erfolgreich gesendeten Transaktionen.
     * Transaktionen, die nicht gesendet werden konnten, sollten nicht in dieser Liste erscheinen.
     * @throws Exception Wenn während des Batch-Versands ein übergeordneter Fehler auftritt,
     *                   der den gesamten Vorgang verhindert (z.B. keine Netzwerkverbindung).
     */
    ArrayList<String> sendBatch() throws Exception;

    /**
     * Sendet eine einzelne, bereits signierte Transaktion an das Ethereum-Netzwerk.
     *
     * @param signedTransactionData Die signierte Transaktion als Hex-String.
     * @return Der Transaktions-Hash bei erfolgreichem Versand.
     * @throws Exception Wenn der Knoten einen Fehler zurückgibt.
     */
    String sendSignedTransaction(String signedTransactionData) throws Exception;
}
