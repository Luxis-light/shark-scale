package offlineTXCreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import offlineWallet.GetWallet;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class OfflineTXCreator implements INetworkConnection, ITXCreator {


    private final GetWallet getWallet;
    private final ArrayList<String> signedTransactions;
    private final Web3j web3j;
    private final String walletAddress; // Die Adresse der Wallet, für die Transaktionen erstellt werden
    private BigInteger currentNonce; // Instanzvariable für die Nonce

    /**
     * Standard-Konstruktor für OfflineTXCreator.
     * Initialisiert Web3j mit Sepolia RPC und ruft die anfängliche Nonce vom Netzwerk ab.
     *
     * @param getWallet Der Wallet-Provider, der Credentials liefert.
     * @throws IOException          wenn ein Netzwerkfehler auftritt.
     * @throws InterruptedException wenn der Thread unterbrochen wird.
     * @throws ExecutionException   wenn ein Fehler bei der asynchronen Ausführung auftritt.
     */
    public OfflineTXCreator(GetWallet getWallet) throws IOException, InterruptedException, ExecutionException {
        this(getWallet, Web3j.build(new HttpService("https://sepolia.drpc.org")));
    }

    /**
     * Konstruktor für OfflineTXCreator, der eine externe Web3j-Instanz injiziert.
     * Ruft die anfängliche Nonce vom Netzwerk ab.
     *
     * @param getWallet Der Wallet-Provider, der Credentials liefert.
     * @param web3j     Die Web3j-Instanz für die Kommunikation mit der Blockchain.
     * @throws IOException wenn ein Netzwerkfehler auftritt.
     * @throws InterruptedException wenn der Thread unterbrochen wird.
     * @throws ExecutionException wenn ein Fehler bei der asynchronen Ausführung auftritt.
     */
    public OfflineTXCreator(GetWallet getWallet, Web3j web3j) throws IOException, InterruptedException, ExecutionException {
        if (getWallet == null) {
            throw new IllegalArgumentException("Wallet-Provider darf nicht null sein");
        }
        if (web3j == null) {
            throw new IllegalArgumentException("Web3j darf nicht null sein");
        }
        this.getWallet = getWallet;
        this.web3j = web3j;
        this.signedTransactions = new ArrayList<>();
        this.walletAddress = getWallet.getCredentials().getAddress(); // Setze die Wallet-Adresse
        this.resyncNonce(); // Rufe die aktuelle Nonce ab und setze sie initial
    }

    /**
     * Konstruktor für OfflineTXCreator, der eine externe Web3j-Instanz und eine anfängliche Nonce injiziert.
     * Dies ist nützlich für Tests oder wenn die Nonce anderweitig bekannt ist.
     *
     * @param getWallet Der Wallet-Provider, der Credentials liefert.
     * @param web3j     Die Web3j-Instanz für die Kommunikation mit der Blockchain.
     * @param initialNonce Die anfänglich zu verwendende Nonce.
     */
    public OfflineTXCreator(GetWallet getWallet, Web3j web3j, BigInteger initialNonce) {
        if (getWallet == null) {
            throw new IllegalArgumentException("Wallet-Provider darf nicht null sein");
        }
        if (web3j == null) {
            throw new IllegalArgumentException("Web3j darf nicht null sein");
        }
        if (initialNonce == null || initialNonce.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Anfängliche Nonce darf nicht null oder negativ sein");
        }
        this.getWallet = getWallet;
        this.web3j = web3j;
        this.signedTransactions = new ArrayList<>();
        this.walletAddress = getWallet.getCredentials().getAddress();
        this.currentNonce = initialNonce; // Setze die Nonce direkt
    }

    /**
     * Ruft die nächste verfügbare Nonce (Transaktionszähler) für die Wallet-Adresse dieses TXCreators
     * vom Ethereum-Netzwerk ab und aktualisiert die interne currentNonce-Variable.
     * Dies sollte aufgerufen werden, wenn die Nonce möglicherweise nicht mehr synchron ist (z.B. nach einem Programmstart
     * oder wenn andere Transaktionen außerhalb dieser Instanz gesendet wurden).
     *
     * @throws IOException          falls ein Netzwerkfehler auftritt.
     * @throws InterruptedException falls der Thread unterbrochen wird.
     * @throws ExecutionException   falls ein Fehler bei der asynchronen Ausführung auftritt.
     */
    public void resyncNonce() throws IOException, InterruptedException, ExecutionException {
        this.currentNonce = web3j.ethGetTransactionCount(this.walletAddress, DefaultBlockParameterName.LATEST).send().getTransactionCount();
        System.out.println("Nonce synchronisiert für " + this.walletAddress + ". Neue Nonce: " + this.currentNonce);
    }

    /**
     * Gibt die aktuell von diesem TXCreator verwendete Nonce zurück.
     * Dies ist die Nonce, die für die nächste zu erstellende Transaktion verwendet wird.
     *
     * @return Die aktuelle Nonce.
     */
    public BigInteger getCurrentNonce() {
        return currentNonce;
    }


    /**
     * Sendet eine bereits signierte Roh-Transaktion an das Sepolia-Netzwerk.
     *
     * @param signedTransactionData Die hex-kodierte, signierte Transaktionsdaten.
     * @return Die Transaktions-Hash, wenn erfolgreich gesendet.
     * @throws Exception wenn das Senden fehlschlägt.
     */
    public String sendSignedTransaction(String signedTransactionData) throws Exception {
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(signedTransactionData).send();

        if (ethSendTransaction.hasError()) {
            // Wirf eine RuntimeException mit der spezifischen Fehlermeldung vom Netzwerk
            throw new RuntimeException("Fehler beim Senden der Transaktion: " + ethSendTransaction.getError().getMessage());
        }

        return ethSendTransaction.getTransactionHash();
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

    /**
     * Ruft die Liste der intern gespeicherten signierten Transaktionen ab.
     *
     * @return Eine Liste von hex-kodierten signierten Transaktionen.
     */
    public ArrayList<String> getSignedTransactions() {
        return new ArrayList<>(this.signedTransactions); // Rückgabe einer Kopie zur Sicherheit
    }


    /**
     * Erstellt eine RawTransaction, lässt diese vom Wallet-Provider signieren
     * und speichert die signierte Transaktion intern in der Liste.
     * Die Nonce wird intern verwaltet und nach dem Erstellen inkrementiert.
     *
     * @param gasPrice Der Gaspreis für die Transaktion (in Wei).
     * @param gasLimit Das Gaslimit für die Transaktion.
     * @param to       Die Empfängeradresse.
     * @param value    Der zu sendende Wert (in Wei).
     * @param data     Optionales Datenfeld für die Transaktion (kann null oder leer sein).
     * @return true, wenn die Transaktion erfolgreich erstellt und signiert wurde, sonst false.
     */
    @Override
    public boolean createTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data) { // Nonce-Parameter entfernt
        try {
            RawTransaction rawTransaction;
            if (data != null && !data.isEmpty()) {
                rawTransaction = RawTransaction.createTransaction(currentNonce, gasPrice, gasLimit, to, value, data); // currentNonce verwendet
            } else {
                rawTransaction = RawTransaction.createEtherTransaction(currentNonce, gasPrice, gasLimit, to, value); // currentNonce verwendet
            }
            // Delegiert die Signierung an die signTransaction-Methode des GetWallet-Interfaces
            String signedTxHex = getWallet.signTransaction(rawTransaction);
            this.signedTransactions.add(signedTxHex); // Signierte Transaktion zur Liste hinzufügen
            currentNonce = currentNonce.add(BigInteger.ONE); // Nonce nach erfolgreicher Erstellung inkrementieren
            return true; // Erfolgreich erstellt und signiert
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen oder Signieren der Transaktion: " + e.getMessage());
            return false; // Fehler aufgetreten
        }
    }

    /**
     * Ruft den aktuellen empfohlenen Gaspreis vom verbundenen Ethereum-Netzwerk ab.
     *
     * @return Den aktuellen Gaspreis in Wei.
     * @throws IOException falls ein Netzwerkfehler auftritt.
     * @throws InterruptedException falls der Thread unterbrochen wird.
     */
    public BigInteger getCurrentGasPrice() throws IOException, InterruptedException {
        return web3j.ethGasPrice().send().getGasPrice();
    }

    /**
     * Gibt die Web3j-Instanz dieses TXCreators zurück.
     * Dies kann nützlich sein, um Netzwerkoperationen direkt auszuführen,
     * die nicht direkt Teil der Transaktionserstellung sind (z.B. Balance-Abfrage).
     *
     * @return Die Web3j-Instanz.
     */
    public Web3j getWeb3j() {
        return web3j;
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

    /**
     * Sendet einen Batch von intern gespeicherten, bereits signierten Transaktionen an das Ethereum-Netzwerk.
     * Nach dem Senden wird die interne Liste der signierten Transaktionen geleert.
     *
     * @return Eine Liste der Transaktions-Hashes der erfolgreich gesendeten Transaktionen.
     * @throws Exception Wenn beim Senden einer Transaktion ein Fehler auftritt.
     */
    @Override
    public ArrayList<String> sendBatch(ArrayList<String> signedTransactions) throws Exception { // Parameter wieder hinzugefügt
        ArrayList<String> transactionHashes = new ArrayList<>();

        for (String signedTxData : signedTransactions) { // Iteriere über den Parameter
            try {
                String txHash = sendSignedTransaction(signedTxData);
                transactionHashes.add(txHash);
                System.out.println("Transaktion gesendet: " + txHash);
            } catch (Exception e) {
                System.err.println("Fehler beim Senden einer Batch-Transaktion: " + signedTxData + ": " + e.getMessage());

            }
        }
        // Die interne Liste signedTransactions wird NICHT hier geleert, da sie als Parameter übergeben wurde.
        // Das Leeren der Liste obliegt nun dem Aufrufer, falls gewünscht.
        signedTransactions.clear();
        return transactionHashes;
    }

}


