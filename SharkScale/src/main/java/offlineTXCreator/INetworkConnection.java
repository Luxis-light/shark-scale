package offlineTXCreator;

import java.util.ArrayList;

/**
 *
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
     * @param signedTransactions Eine Liste von signierten Transaktionen.
     * @return Eine Liste von Transaktions-Hashes für die erfolgreich gesendeten Transaktionen.
     * Transaktionen, die nicht gesendet werden konnten, sollten nicht in dieser Liste erscheinen.
     * @throws Exception Wenn während des Batch-Versands ein übergeordneter Fehler auftritt,
     *                   der den gesamten Vorgang verhindert (z.B. keine Netzwerkverbindung).
     */
    ArrayList<String> sendBatch(ArrayList<String> signedTransactions) throws Exception;
}
