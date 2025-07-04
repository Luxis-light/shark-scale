package offlineWallet.keystorefile;

import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.IOException;

/**
 * Eine konkrete Implementierung von IKeystoreReader, die web3j's WalletUtils
 * zum Laden von Ethereum-Standard-Keystore-Dateien verwendet.
 */
public class Web3jKeystoreReader implements IKeystoreReader {

    @Override
    public Credentials loadCredentials(String password, File sourceFile) throws IOException, CipherException {
        return WalletUtils.loadCredentials(password, sourceFile);
    }
}