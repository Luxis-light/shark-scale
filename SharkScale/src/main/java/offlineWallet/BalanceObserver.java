package offlineWallet;

import java.math.BigInteger;

/**
 * Das Observer-Interface. Jede Klasse, die über eine
 * Kontostandsänderung informiert werden möchte, implementiert dieses Interface.
 */
public interface BalanceObserver {
    /**
     * @param source Die Wallet, deren Kontostand sich geändert hat.
     * @param newBalance Der neue Kontostand.
     */
    void updateBalance(GetWallet source, BigInteger newBalance); // <--- GEÄNDERT
}