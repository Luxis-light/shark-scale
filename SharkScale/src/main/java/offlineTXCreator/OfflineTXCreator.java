package offlineTXCreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import offlineWallet.GetWallet;
import org.jetbrains.annotations.NotNull;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;

import java.io.*;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class OfflineTXCreator implements INetworkConnection, ITXCreator {


    private final ArrayList<TransactionJob> pendingTransactionJobs;


    private final GetWallet getWallet;
    private BigInteger currentNonce;
    private final Web3j web3j;
    private final String walletAddress;
    /**
     * Standard-Konstruktor für OfflineTXCreator.
     * Initialisiert Web3j mit Sepolia RPC und ruft die anfängliche Nonce vom Netzwerk ab.
     * @param getWallet Der Wallet-Provider, der Credentials liefert.
     * @throws IOException wenn ein Netzwerkfehler auftritt.
     * @throws InterruptedException wenn der Thread unterbrochen wird.
     * @throws ExecutionException wenn ein Fehler bei der asynchronen Ausführung auftritt.
     */
    public OfflineTXCreator(GetWallet getWallet) throws IOException, InterruptedException, ExecutionException {
        this(getWallet, Web3j.build(new HttpService("https://sepolia.drpc.org")));
    }

    /**
     * Konstruktor für OfflineTXCreator, der eine externe Web3j-Instanz injiziert.
     * Ruft die anfängliche Nonce vom Netzwerk ab.
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
        this.pendingTransactionJobs = new ArrayList<>(); // Initialisierung der neuen Liste
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
        this.pendingTransactionJobs = new ArrayList<>(); // Initialisierung der neuen Liste
        this.walletAddress = getWallet.getCredentials().getAddress();
        this.currentNonce = initialNonce; // Setze die Nonce direkt
    }

    /**
     * Ruft die nächste verfügbare Nonce (Transaktionszähler) für die Wallet-Adresse dieses TXCreators
     * vom Ethereum-Netzwerk ab und aktualisiert die interne currentNonce-Variable.
     * Dies sollte aufgerufen werden, wenn die Nonce möglicherweise nicht mehr synchron ist (z.B. nach einem Programmstart
     * oder wenn andere Transaktionen außerhalb dieser Instanz gesendet wurden).
     * @throws IOException falls ein Netzwerkfehler auftritt.
     * @throws InterruptedException falls der Thread unterbrochen wird.
     * @throws ExecutionException falls ein Fehler bei der asynchronen Ausführung auftritt.
     */
    public void resyncNonce() throws IOException, InterruptedException, ExecutionException {
        this.currentNonce = web3j.ethGetTransactionCount(this.walletAddress, DefaultBlockParameterName.LATEST).send().getTransactionCount();
        System.out.println("Nonce synchronisiert für " + this.walletAddress + ". Neue Nonce: " + this.currentNonce);
    }

    /**
     * Speichert die vollständige Liste der ausstehenden TransactionJob-Objekte als JSON in eine Datei.
     * Diese Methode LEERT die Liste nach dem Speichern.
     *
     * @param directory Das Verzeichnis, in dem die Datei gespeichert werden soll.
     * @param filename  Der Name der zu erstellenden JSON-Datei.
     * @throws IOException Wenn beim Schreiben der Datei ein Fehler auftritt.
     */
    public void saveAndClearTransactionsToJson(File directory, String filename) throws IOException {
        // 1. Speichere die vollständigen Job-Objekte, nicht nur die Hex-Strings.
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(this.pendingTransactionJobs);

        // 2. Erstelle den Dateipfad auf die korrekte, plattformunabhängige Weise.
        File finalFile = new File(directory, filename);

        // Stelle sicher, dass das übergeordnete Verzeichnis existiert.
        if (!directory.exists()) {
            directory.mkdirs(); // Erstellt das Verzeichnis, falls es nicht existiert.
        }
        try (FileWriter writer = new FileWriter(finalFile)) {
            writer.write(json);
        }

        this.pendingTransactionJobs.clear();

        System.out.println("Transaktions-Jobs erfolgreich in JSON gespeichert unter: " + finalFile.getAbsolutePath());
    }


    /**
     * Lädt eine Liste von TransactionJob-Objekten aus einer JSON-Datei, ersetzt damit
     * die Liste der aktuell ausstehenden Transaktionen und löscht die Quelldatei
     * nach dem erfolgreichen Laden.
     * Die interne Nonce des Creators wird hierbei NICHT verändert.
     *
     * @param filePath Der vollständige Pfad zur JSON-Datei.
     * @throws IOException Wenn beim Lesen oder Löschen der Datei ein Fehler auftritt.
     */
    public void loadTransactionsFromJsonAndDelete(String filePath) throws IOException {
        File sourceFile = new File(filePath);

        // Prüfen, ob die Datei überhaupt existiert, bevor wir versuchen, sie zu lesen.
        if (!sourceFile.exists()) {
            System.out.println("Datei nicht gefunden: " + filePath);
            return;
        }

        Gson gson = new Gson();
        Type transactionJobListType = new TypeToken<ArrayList<TransactionJob>>() {
        }.getType();

        try (Reader reader = new FileReader(sourceFile)) {
            ArrayList<TransactionJob> loadedJobs = gson.fromJson(reader, transactionJobListType);

            if (loadedJobs != null && !loadedJobs.isEmpty()) {
                this.pendingTransactionJobs.clear();
                this.pendingTransactionJobs.addAll(loadedJobs);
                System.out.println(loadedJobs.size() + " Transaktions-Jobs erfolgreich aus " + filePath + " geladen.");

            } else {
                System.out.println("Keine Transaktions-Jobs in " + filePath + " gefunden oder Datei ist leer.");
            }
        }

        // Jetzt, wo der Lesevorgang abgeschlossen ist, kann die Datei sicher gelöscht werden.
        try {
            Files.delete(Paths.get(filePath));
            System.out.println("JSON-Datei erfolgreich gelöscht: " + filePath);
        } catch (IOException e) {
            System.err.println("Fehler beim Löschen der Datei: " + filePath);
            // Wirf die Exception weiter, damit der aufrufende Code darauf reagieren kann.
            throw e;
        }
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
     * Ruft die Liste der intern gespeicherten signierten Transaktionen als Hex-Strings ab.
     * @return Eine Liste von hex-kodierten signierten Transaktionen.
     */
    public List<String> getSignedTransactions() {
        List<String> hexStrings = new ArrayList<>();
        for (TransactionJob job : this.pendingTransactionJobs) {
            hexStrings.add(job.signedHex);
        }
        return hexStrings; // Rückgabe einer Kopie zur Sicherheit
    }

    /**
     * Erstellt eine RawTransaction, lässt diese vom Wallet-Provider signieren
     * und speichert die Transaktionsdetails sowie den signierten Hex-String intern in der Liste.
     * Die Nonce wird intern verwaltet und nach dem Erstellen inkrementiert.
     *
     * @param gasPrice   Der Gaspreis für die Transaktion (in Wei).
     * @param gasLimit   Das Gaslimit für die Transaktion.
     * @param to         Die Empfängeradresse.
     * @param value      Der zu sendende Wert (in Wei).
     * @param data       Optionales Datenfeld für die Transaktion (kann null oder leer sein).
     * @return true, wenn die Transaktion erfolgreich erstellt und signiert wurde, sonst false.
     */
    @Override
    public boolean createTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data) {
        try {
            RawTransaction rawTransaction;
            if (data != null && !data.isEmpty()) {
                rawTransaction = RawTransaction.createTransaction(currentNonce, gasPrice, gasLimit, to, value, data);
            } else {
                rawTransaction = RawTransaction.createEtherTransaction(currentNonce, gasPrice, gasLimit, to, value);
            }
            String signedTxHex = getWallet.signTransaction(rawTransaction);
            // Speichere den Job mit den ursprünglichen Parametern und dem signierten Hex
            this.pendingTransactionJobs.add(new TransactionJob(gasPrice, gasLimit, to, value, data, signedTxHex));
            currentNonce = currentNonce.add(BigInteger.ONE); // Nonce nach erfolgreicher Erstellung inkrementieren
            return true; // Erfolgreich erstellt und signiert
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen oder Signieren der Transaktion: " + e.getMessage());
            return false; // Fehler aufgetreten
        }
    }

    @Override
    public boolean createTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data) {
        try {
            RawTransaction rawTransaction;
            if (data != null && !data.isEmpty()) {
                rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data);
            } else {
                rawTransaction = RawTransaction.createEtherTransaction(currentNonce, gasPrice, gasLimit, to, value);
            }
            String signedTxHex = getWallet.signTransaction(rawTransaction);
            // Speichere den Job mit den ursprünglichen Parametern und dem signierten Hex
            this.pendingTransactionJobs.add(new TransactionJob(gasPrice, gasLimit, to, value, data, signedTxHex));
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
    public BigInteger fetchCurrentGasPrice() throws IOException, InterruptedException {
        return web3j.ethGasPrice().send().getGasPrice();
    }


    /**
     * Sendet einen Batch von intern gespeicherten, bereits signierten Transaktionen an das Ethereum-Netzwerk.
     * Bei einem Nonce-Fehler wird versucht, die Transaktion mit einer aktualisierten Nonce neu zu signieren und zu senden.
     * Die interne Liste der ausstehenden Transaktionen wird nach der Verarbeitung geleert (erfolgreich oder nicht).
     *
     * @return Eine Liste der Transaktions-Hashes der erfolgreich gesendeten Transaktionen.
     * @throws Exception Wenn ein unerwarteter Fehler beim Senden einer Transaktion auftritt, der nicht mit der Nonce zusammenhängt und nicht behoben werden kann.
     */
    @Override
    public ArrayList<String> sendBatch() throws Exception { // Parameter entfernt, arbeitet intern
        ArrayList<String> transactionHashes = new ArrayList<>();
        ArrayList<TransactionJob> jobsToProcess = new ArrayList<>(this.pendingTransactionJobs); // Eine Kopie, um Änderungen während der Iteration zu ermöglichen


        this.pendingTransactionJobs.clear();

        for (TransactionJob job : jobsToProcess) {
            try {
                String txHash = sendSignedTransaction(job.signedHex);
                transactionHashes.add(txHash);
                System.out.println("Transaktion gesendet: " + txHash);
            } catch (RuntimeException e) { // Fang die RuntimeException von sendSignedTransaction
                System.err.println("Fehler beim Senden einer Transaktion (erste Versuch): " + job.signedHex + ": " + e.getMessage());

                correction(job, e, transactionHashes);
            }
        }
        return transactionHashes;
    }


    private void correction(TransactionJob job, RuntimeException e, ArrayList<String> transactionHashes) {
        // Überprüfe, ob es ein Nonce-Fehler ist
        if (e.getMessage() != null && (e.getMessage().contains("nonce too low") || e.getMessage().contains("already known") || e.getMessage().contains("invalid nonce"))) {
            System.out.println("Nonce-Fehler erkannt. Versuche Nonce zu synchronisieren und Transaktion neu zu signieren.");
            try {
                this.resyncNonce(); // Nonce aktualisieren

                // Erstelle RawTransaction mit der NEUEN Nonce
                RawTransaction rawTransactionToResign = getRawTransaction(job);
                // Neu signieren
                String reSignedTxHex = getWallet.signTransaction(rawTransactionToResign);
                job.signedHex = reSignedTxHex; // Aktualisiere den signierten Hex im Job

                retryTx(job, transactionHashes);

            } catch (Exception retryEx) {
                System.err.println("FEHLER: Transaktion nach Nonce-Korrektur immer noch fehlgeschlagen: " + job.signedHex + ": " + retryEx.getMessage());
                // Hier könnte man den Job zu einer "failedTransactions"-Liste hinzufügen
            }
        } else {
            // Anderer, nicht-Nonce-bezogener Fehler
            System.err.println("FEHLER: Nicht-Nonce-bezogener Transaktionsfehler: " + job.signedHex + ": " + e.getMessage());
        }
    }

    private void retryTx(TransactionJob job, ArrayList<String> transactionHashes) throws Exception {
        // Erneuten Sendeversuch
        String txHash = sendSignedTransaction(job.signedHex);
        transactionHashes.add(txHash);
        System.out.println("Transaktion nach Nonce-Korrektur gesendet: " + txHash);

        // WICHTIG: Nonce auch nach erfolgreichem Re-Try inkrementieren
        // Diese Zeile sollte eigentlich in createTransaction sein, aber
        // wenn wir hier re-signieren, müssen wir die Nonce-Kette manuell fortführen.
        // Alternativ könnte createTransaction die Nonce NICHT inkrementieren
        // und sendBatch die Verantwortung dafür übernehmen.
        // Für diesen Ansatz inkrementieren wir die Nonce hier nach einem erfolgreichen Re-Try.
        this.currentNonce = this.currentNonce.add(BigInteger.ONE);
    }

    @NotNull
    private RawTransaction getRawTransaction(TransactionJob job) {
        RawTransaction rawTransactionToResign;
        if (job.data != null && !job.data.isEmpty()) {
            rawTransactionToResign = RawTransaction.createTransaction(this.currentNonce, job.gasPrice, job.gasLimit, job.to, job.value, job.data);
        } else {
            rawTransactionToResign = RawTransaction.createEtherTransaction(this.currentNonce, job.gasPrice, job.gasLimit, job.to, job.value);
        }
        return rawTransactionToResign;
    }

    /**
     * Gibt die Web3j-Instanz dieses TXCreators zurück.
     * Dies kann nützlich sein, um Netzwerkoperationen direkt auszuführen,
     * die nicht direkt Teil der Transaktionserstellung sind (z.B. Balance-Abfrage).
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

    private static class TransactionJob {
        final BigInteger gasPrice;
        final BigInteger gasLimit;
        final String to;
        final BigInteger value;
        final String data;
        String signedHex;

        TransactionJob(BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data, String signedHex) {
            this.gasPrice = gasPrice;
            this.gasLimit = gasLimit;
            this.to = to;
            this.value = value;
            this.data = data;
            this.signedHex = signedHex;
        }
    }
}
