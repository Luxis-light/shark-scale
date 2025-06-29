package offlineTXCreator;

import java.math.BigInteger;

/**
 *
 */
public interface ITXCreator {

	/**
	 * Erstellt eine RawTransaction, lässt diese vom Wallet-Provider signieren
	 * und speichert die signierte Transaktion intern in der Liste.
	 * Diese Methode ersetzt die frühere 'createSignedTransaction'.
	 *

	 * @param gasPrice   Der Gaspreis für die Transaktion (in Wei).
	 * @param gasLimit   Das Gaslimit für die Transaktion.
	 * @param to         Die Empfängeradresse.
	 * @param value      Der zu sendende Wert (in Wei).
	 * @param data       Optionales Datenfeld für die Transaktion (kann null oder leer sein).
	 * @return Die signierte Transaktion als Hex-String.
	 */
	boolean createTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data);
	
}
