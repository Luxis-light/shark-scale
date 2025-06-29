import offlineTXCreator.OfflineTXCreator;
import offlineWallet.OfflineWallet;
import offlineWallet.keystorefile.GenerateKeystorefile;
import offlineWallet.keystorefile.KeystoreGenerator;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.File;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public class demo {
    public static void main(String[] args) throws Exception { // Allgemeine Exception für Einfachheit im Beispiel
        Web3j web3j = Web3j.build(new HttpService("https://sepolia.drpc.org"));

        GenerateKeystorefile generateKeystorefile = new KeystoreGenerator();

        // Hinweis: Es wird angenommen, dass die Keystore-Dateien unter diesen Pfaden existieren.
        // Diese Pfade sind fest kodiert und müssen auf deinem System existieren.
        String exportedFilePath1 = "C://BlockchainKey/UTC--2025-06-28T19-11-19.583110000Z--0295d8a45fab22cb581896a5996171dc9148a074.json";
        String exportedFilePath2 = "C://BlockchainKey/Ichbincool.js";


        // --- Wallets aus den exportierten Dateien laden ---
        Optional<OfflineWallet> offlineWallet1Optional = OfflineWallet.loadWalletFromKeystore("Falches Passwor", new File(exportedFilePath1), generateKeystorefile);
        Optional<OfflineWallet> offlineWallet2Optional = OfflineWallet.loadWalletFromKeystore("Falches Passwort", new File(exportedFilePath2), generateKeystorefile);

        if (offlineWallet1Optional.isPresent() && offlineWallet2Optional.isPresent()) {
            OfflineWallet wallet1 = offlineWallet1Optional.get();
            OfflineWallet wallet2 = offlineWallet2Optional.get();

            OfflineTXCreator txCreator1 = new OfflineTXCreator(wallet1, web3j);
            OfflineTXCreator txCreator2 = new OfflineTXCreator(wallet2, web3j);


            System.out.println("\nSynchronisiere Nonces der TXCreator...");
            txCreator1.resyncNonce();
            txCreator2.resyncNonce();

            System.out.println("Aktuelle Nonce für Wallet 1 (intern in txCreator1): " + txCreator1.getCurrentNonce());
            System.out.println("Aktuelle Nonce für Wallet 2 (intern in txCreator2): " + txCreator2.getCurrentNonce());


            BigInteger gasPrice = txCreator1.getCurrentGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(21000);
            BigInteger valueToSend = new BigInteger("1000000000000000");

            // --- Transaktionen von Wallet 2 zu Wallet 1 erstellen ---
            System.out.println("\nErstelle Transaktionen von Wallet 2 zu Wallet 1...");

            // Mehrere Transaktionen von Wallet 2 erstellen - Nonce wird automatisch vom txCreator2 inkrementiert
            boolean tx1Created = txCreator2.createTransaction(gasPrice, gasLimit, wallet1.getHexadresse(), valueToSend, null);
            if (tx1Created) {
                System.out.println("Erste Transaktion von Wallet 2 erstellt.");
            }

            boolean tx2Created = txCreator2.createTransaction(gasPrice, gasLimit, wallet1.getHexadresse(), valueToSend, null
            );
            if (tx2Created) {
                System.out.println("Zweite Transaktion von Wallet 2 erstellt.");
            }

            boolean tx3Created = txCreator2.createTransaction( // Nonce wird intern verwendet
                    gasPrice,
                    gasLimit,
                    wallet1.getHexadresse(),
                    valueToSend,
                    null
            );
            if (tx3Created) {
                System.out.println("Dritte Transaktion von Wallet 2 erstellt.");
            }

            System.out.println("\nSignierte Transaktionen in der Liste von txCreator2:");
            txCreator2.getSignedTransactions().forEach(tx -> System.out.println("  " + tx.substring(0, Math.min(tx.length(), 70)) + "..."));

            System.out.println("\nWallet-Stände vor dem Senden der Transaktionen:");
            System.out.println("Wallet 1 (" + wallet1.getHexadresse() + ") Balance: " + wallet1.fetchBalance(web3j));
            System.out.println("Wallet 2 (" + wallet2.getHexadresse() + ") Balance: " + wallet2.fetchBalance(web3j));

            System.out.println("\n============ Transaktionen werden gesendet =============");
            // Sende die Transaktionen, die in txCreator2 gesammelt wurden
            List<String> transactionHashes = txCreator2.sendBatch(txCreator2.getSignedTransactions());
            transactionHashes.forEach(hash -> System.out.println("Gesendeter Transaktions-Hash: " + hash));
            System.out.println("========================================================\n");

            System.out.println("Warte 20 Sekunden auf die Verarbeitung der Transaktionen...");
            Thread.sleep(20000);

            System.out.println("\nSynchronisiere Nonces der TXCreator nach dem Senden...");
            txCreator1.resyncNonce();
            txCreator2.resyncNonce();

            System.out.println("Wallet-Stände nach dem Senden der Transaktionen:");
            System.out.println("Wallet 1 (" + wallet1.getHexadresse() + ") Balance: " + wallet1.fetchBalance(web3j));
            System.out.println("Wallet 2 (" + wallet2.getHexadresse() + ") Balance: " + wallet2.fetchBalance(web3j));

        } else {
            System.err.println("Mindestens ein Wallet konnte NICHT geladen werden. Programm beendet.");
        }

        // Web3j Instanz sauber herunterfahren
        if (web3j != null) {
            web3j.shutdown();
            System.out.println("Web3j-Instanz heruntergefahren.");
        }
        System.out.println("--- Programmende ---");
    }
}
