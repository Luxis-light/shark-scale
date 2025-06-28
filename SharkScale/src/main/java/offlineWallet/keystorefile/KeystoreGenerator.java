package offlineWallet.keystorefile;

import org.web3j.crypto.CipherException;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.IOException;

/**
 * Konkrete Implementierung des GenerateKeystorefile-Interfaces unter Verwendung von web3j.crypto.WalletUtils.
 */
public class KeystoreGenerator implements GenerateKeystorefile {

    @Override
    public String generateKeystoreFile(String password, ECKeyPair ecKeyPair, File destinationDirectory, boolean includeTimestamp) throws CipherException, IOException {
        return WalletUtils.generateWalletFile(password, ecKeyPair, destinationDirectory, includeTimestamp);
    }
}
