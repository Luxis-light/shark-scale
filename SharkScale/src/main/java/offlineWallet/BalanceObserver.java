package offlineWallet;

import java.math.BigInteger;

/**
 * Das Observer-Interface. Jede Klasse, die über eine
 * Kontostandsänderung informiert werden möchte, implementiert dieses Interface.
 */
public interface BalanceObserver {
    /**
     * Diese Methode wird vom Subject aufgerufen, wenn sich der Kontostand ändert.
     *
     * @param newBalance Der neue Kontostand.
     */
    void updateBalance(BigInteger newBalance);
}
