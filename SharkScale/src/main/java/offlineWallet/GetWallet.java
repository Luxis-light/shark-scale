package offlineWallet;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;

/**
 * Interface zum Abrufen von Wallet-Anmeldeinformationen (Credentials), die für die Transaktionssignierung benötigt werden.
 */
public interface GetWallet {

    /**
     * Ruft das Web3j Credentials-Objekt ab, das dieser Wallet zugeordnet ist.
     * Das Credentials-Objekt enthält das ECKeyPair (private und öffentliche Schlüssel)
     * und die Ethereum-Adresse, welche für das Signieren von Transaktionen unerlässlich sind.
     *
     * @return Das Credentials-Objekt der Wallet.
     */
    Credentials getCredentials();

    /**
     * Signiert eine gegebene RawTransaction mit den privaten Schlüsseln dieser Wallet.
     * Der private Schlüssel wird dabei nicht nach außen gegeben, sondern intern für die Signatur verwendet.
     *
     * @param transaction Die zu signierende Transaktion.
     * @return Der signierte Transaktions-Hex-String, bereit zum Senden.
     */
    String signTransaction(RawTransaction transaction);
}
