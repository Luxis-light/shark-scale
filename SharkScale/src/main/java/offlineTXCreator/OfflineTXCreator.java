package offlineTXCreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import offlineWallet.GetWallet;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;

public class OfflineTXCreator implements INetworkConnection, ITXCreator {


    private final GetWallet getWallet;
    private final ArrayList<String> signedTransactions;
    private final Web3j web3j;
    private final String sepoliaRpcUrl = "https://sepolia.drpc.org";

    /**
     * Standard-Konstruktor für OfflineTXCreator, der eine Web3j-Instanz intern erstellt.
     * Nutzt den Sepolia RPC URL als Standard.
     *
     * @param getWallet Der Wallet-Provider, der Credentials liefert.
     */
    public OfflineTXCreator(GetWallet getWallet) {
        if (getWallet == null) {
            throw new IllegalArgumentException("Wallet-Provider darf nicht null sein");
        }
        this.getWallet = getWallet;
        this.web3j = Web3j.build(new HttpService(sepoliaRpcUrl));
        this.signedTransactions = new ArrayList<>();
    }

    /**
     * Konstruktor für OfflineTXCreator, der eine externe Web3j-Instanz injiziert.
     * Dies ermöglicht eine bessere Testbarkeit (Mocking von Web3j).
     *
     * @param getWallet Der Wallet-Provider, der Credentials liefert.
     * @param web3j     Die Web3j-Instanz für die Kommunikation mit der Blockchain.
     */
    public OfflineTXCreator(GetWallet getWallet, Web3j web3j) {
        if (getWallet == null) {
            throw new IllegalArgumentException("Wallet-Provider darf nicht null sein");
        }
        if (web3j == null) { // Zusätzliche Prüfung für die injizierte Web3j-Instanz
            throw new IllegalArgumentException("Web3j darf nicht null sein");
        }
        this.getWallet = getWallet;
        this.web3j = web3j; // Web3j wird jetzt injiziert
        this.signedTransactions = new ArrayList<>();
    }

    public ArrayList<String> getSignedTransactions() {
        return signedTransactions;
    }

    /**
     * Speichert die Liste der signierten Transaktionen als JSON in einer Datei.
     * Die JSON-Ausgabe wird schön formatiert (pretty printed).
     *
     * @param filePath Der vollständige Pfad zur Datei, in die gespeichert werden soll (z.B. "data/signed_transactions.json").
     * @throws IOException Wenn beim Schreiben der Datei ein Fehler auftritt (z.B. Berechtigungsprobleme, Verzeichnis nicht gefunden).
     */
    public void saveSignedTransactionsToJson(String filePath) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(this.signedTransactions);

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(json);
        }
        System.out.println("Signierte Transaktionen erfolgreich in JSON gespeichert unter: " + filePath);
    }


    @Override
    public boolean createTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data) {
        try {
            RawTransaction rawTransaction;
            if (data != null && !data.isEmpty()) {
                rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data);
            } else {
                rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, to, value);
            }
            // Delegiert die Signierung an die signTransaction-Methode des GetWallet-Interfaces
            String signedTxHex = getWallet.signTransaction(rawTransaction);
            this.signedTransactions.add(signedTxHex); // Signierte Transaktion zur Liste hinzufügen
            return true; // Erfolgreich erstellt und signiert
        } catch (Exception e) {
            // Hier könntest du eine detailliertere Fehlerbehandlung hinzufügen
            System.err.println("Fehler beim Erstellen oder Signieren der Transaktion: " + e.getMessage());
            return false; // Fehler aufgetreten
        }
    }

    /**
     * Ruft den aktuellen empfohlenen Gaspreis vom verbundenen Ethereum-Netzwerk ab.
     *
     * @return Den aktuellen Gaspreis in Wei.
     * @throws IOException          falls ein Netzwerkfehler auftritt.
     * @throws InterruptedException falls der Thread unterbrochen wird.
     */
    public BigInteger fetchCurrentGasPrice() throws IOException, InterruptedException {
        // web3j.ethGasPrice() fragt den Gaspreis ab
        // .send() sendet die Anfrage synchron
        // .getGasPrice() holt den BigInteger-Wert aus der Antwort
        return web3j.ethGasPrice().send().getGasPrice();
    }

    @Override
    public boolean isOnline() {
        try {
            web3j.ethBlockNumber().send(); // fragt nach aktueller Blocknummer des Netzwerks
            return true;
        } catch (Exception e) {
            System.err.println("Netzwerkverbindungsproblem (isOnline Check): " + e.getMessage());
            return false;
        }
    }

    @Override
    public ArrayList<String> sendBatch(ArrayList<String> signedTransactions) throws Exception {
        ArrayList<String> transactionHashes = new ArrayList<>();

        for (String signedTx : signedTransactions) {
            try {
                EthSendTransaction response = web3j.ethSendRawTransaction(signedTx).send(); // sende Raw Transaktion

                if (response.hasError()) {
                    System.err.println("Fehlerhafte Transaktion: " + signedTx);
                    System.err.println("Fehler beim Senden der Transaktion: " + response.getError().getMessage());
                } else {
                    String txHash = response.getTransactionHash();
                    transactionHashes.add(txHash);
                }
            } catch (Exception e) {
                System.err.println("Exception beim Senden der Transaktion: " + e.getMessage());
            }
        }

        return transactionHashes;
    }
}
