package offlineTXCreator;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Das ITXCreator-Interface definiert Methoden zum Erstellen, Signieren, Speichern und Laden von
 * Blockchain-Transaktionen. Es ermöglicht die Offline-Erstellung und Verwaltung von Transaktionen,
 * die später an ein Netzwerk gesendet werden können.
 */
public interface ITXCreator {

	/**
	 * Erstellt eine RawTransaction, lässt diese vom Wallet-Provider signieren
	 * und speichert die signierte Transaktion intern in der Liste.
	 *
	 * @param gasPrice   Der Gaspreis für die Transaktion (in Wei).
	 * @param gasLimit   Das Gaslimit für die Transaktion.
	 * @param to         Die Empfängeradresse.
	 * @param value      Der zu sendende Wert (in Wei).
	 * @param data       Optionales Datenfeld für die Transaktion (kann null oder leer sein).
	 * @return Die signierte Transaktion als Hex-String.
	 */
	boolean createTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data);

	/**
	 * Erstellt eine RawTransaction, lässt diese vom Wallet-Provider signieren
	 * und speichert die signierte Transaktion intern in der Liste.
	 * Zudem darf man manuel den nonce wählen
	 *
	 * @param nonce    Der nonce für die Transaktion.
	 * @param gasPrice Der Gaspreis für die Transaktion (in Wei).
	 * @param gasLimit Das Gaslimit für die Transaktion.
	 * @param to       Die Empfängeradresse.
	 * @param value    Der zu sendende Wert (in Wei).
	 * @param data     Optionales Datenfeld für die Transaktion (kann null oder leer sein).
	 * @return Die signierte Transaktion als Hex-String.
	 */
	boolean createTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data);

	/**
	 * Speichert Transaktionen als JSON Dokument.
	 * Die Transaktionen wird aus der anschließend Transaktionsliste entfernt.
	 *
	 * @param directory Ordner zum Speichern der Transaktionen
	 * @param filename Name der Datei
	 * @return true, wenn Transaktionen erfolgreich exportiert und aus der Transaktionsliste entfernt wurden
	 */
	boolean saveAndClearTransactionsToJson(File directory, String filename) throws IOException;

	/**
	 * Lädt Transaktionen aus einer JSON in die Transaktionsliste.
	 * Das JSON Dokument wird anschließend gelöscht.
	 *
	 * @param filePath Pfad des JSON Objekts
	 * @return true, wenn die JSON erfolgreich geladen und gelöscht wurde
	 */
	boolean loadTransactionsFromJsonAndDelete(String filePath) throws IOException;

}
